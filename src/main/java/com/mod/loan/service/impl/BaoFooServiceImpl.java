package com.mod.loan.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.baofoo.config.BaofooPayConfig;
import com.mod.loan.baofoo.rsa.RsaCodingUtil;
import com.mod.loan.baofoo.rsa.SignatureUtils;
import com.mod.loan.baofoo.util.FormatUtil;
import com.mod.loan.baofoo.util.HttpUtil;
import com.mod.loan.baofoo.util.SecurityUtil;
import com.mod.loan.mapper.UserBankMapper;
import com.mod.loan.model.UserBank;
import com.mod.loan.service.BaoFooService;
import com.mod.loan.service.UserBankService;
import com.mod.loan.util.ConstantUtils;
import com.mod.loan.util.RandomUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author yutian
 */
@Slf4j
@Service
public class BaoFooServiceImpl implements BaoFooService {

    @Autowired
    private UserBankService userBankService;

    @Autowired
    private BaofooPayConfig baofooPayConfig;

    @Autowired
    private UserBankMapper userBankMapper;

    @Override
    public void bindQuery() {
        List<UserBank> list = userBankMapper.findForeignIdNotNull();
        log.info("BaoFooServiceImpl.bindQuery. count={}", list.size());
        for (UserBank userBank : list) {
            if (StringUtils.isBlank(userBank.getForeignId()) || ConstantUtils.ZERO == userBank.getCardStatus()) {
                log.info("不需要发送请求信息:{}", JSONObject.toJSONString(userBank));
                continue;
            }
            String protocolNo = null;
            try {
                protocolNo = bindQuery(userBank.getCardNo(), userBank.getUid() + "");
            } catch (Exception e) {
                log.error("BaoFooServiceImpl.bindQuery, find protocolNo err, userId={}", userBank.getUid(), e);
            }
            if (StringUtils.isBlank(protocolNo)) {
                log.error("BaoFooServiceImpl.bindQuery, find protocolNo null, userId={}", userBank.getUid());
                return;
            }
            if (!protocolNo.equals(userBank.getForeignId())) {
                log.info("更新用户:{},更新卡号:{}", userBank.getUid(), userBank.getCardNo());
                userBank.setForeignId(protocolNo);
                userBank.setUpdateTime(new Date());
                userBankService.updateByPrimaryKey(userBank);
                log.info("BaoFooServiceImpl.bindQuery, update protocolNo success, userId={}", userBank.getUid());
            } else {
                log.info("BaoFooServiceImpl.bindQuery, xiangtong, userId={}, protocolNo={}, protocolNoOld={}",
                        userBank.getUid(), protocolNo, userBank.getForeignId());
            }
        }
    }

    /**
     * 绑卡查询
     */
    private String bindQuery(String userBank, String userId) throws Exception {
        //报文发送日期时间
        String sendTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        //商户私钥
        String pfxPath = baofooPayConfig.getBaofooRepayPriKeyPath();
        //宝付公钥
        String cerPath = baofooPayConfig.getBaofooRepayPubKeyPath();

        //商户自定义（可随机生成  商户自定义(AES key长度为=16位)）
        String aesKey = "4f66405c4f66405c";
        //使用接收方的公钥加密后的对称密钥，并做Base64转码，明文01|对称密钥，01代表AES[密码商户自定义]
        String dgtlEnvlp = "01|" + aesKey;
        //公钥加密
        dgtlEnvlp = RsaCodingUtil.encryptByPubCerFile(SecurityUtil.Base64Encode(dgtlEnvlp), cerPath);
        //银行卡号
        String accNo = userBank;
        //先BASE64后进行AES加密
        accNo = SecurityUtil.AesEncrypt(SecurityUtil.Base64Encode(accNo), aesKey);

        Map<String, String> dateArray = new TreeMap<>();
        dateArray.put("send_time", sendTime);
        //报文流水号
        dateArray.put("msg_id", "TISN" + System.currentTimeMillis() + RandomUtils.generateRandomNum(6));
        dateArray.put("version", baofooPayConfig.getBaofooRepayVersion());
        dateArray.put("terminal_id", baofooPayConfig.getBaofooRepayTerminalId());
        //交易类型
        dateArray.put("txn_type", "03");
        dateArray.put("member_id", baofooPayConfig.getBaofooRepayMemberId());
        dateArray.put("dgtl_envlp", dgtlEnvlp);
        //用户在平台的唯一ID
        dateArray.put("user_id", userId);
        //银行卡号密文[与user_id必须其中一个有值]
        dateArray.put("acc_no", "");

        String signVStr = FormatUtil.coverMap2String(dateArray);
        //签名
        String signature = SecurityUtil.sha1X16(signVStr, "UTF-8");
        String sign = SignatureUtils.encryptByRSA(signature, pfxPath, baofooPayConfig.getBaofooRepayKeyPassword());
        //签名域
        dateArray.put("signature", sign);

        log.info("绑卡查询请求开始");
        String postString = HttpUtil.RequestForm("https://public.baofoo.com/cutpayment/protocol/backTransRequest", dateArray);

        Map<String, String> returnData = FormatUtil.getParm(postString);

        log.info("绑卡查询返回参数:{}", JSON.toJSONString(returnData));

        if (!returnData.containsKey("signature")) {
            throw new Exception("缺少验签参数！");
        }

        String rSign = returnData.get("signature");
        //需要删除签名字段
        returnData.remove("signature");
        String rSignVStr = FormatUtil.coverMap2String(returnData);
        //签名
        String rSignature = SecurityUtil.sha1X16(rSignVStr, "UTF-8");

        if (!SignatureUtils.verifySignature(cerPath, rSignature, rSign)) {
            log.error("宝付查询验签失败");
            return null;
        }
        if (!returnData.containsKey("resp_code")) {
            throw new Exception("缺少resp_code参数！");
        }
        if ("S".equals(returnData.get("resp_code"))) {
            if (!returnData.containsKey("dgtl_envlp")) {
                throw new Exception("缺少dgtl_envlp参数！");
            }
            String rDgtlEnvlp = SecurityUtil.Base64Decode(RsaCodingUtil.decryptByPriPfxFile(returnData.get("dgtl_envlp"), pfxPath, baofooPayConfig.getBaofooRepayKeyPassword()));
            //获取返回的AESkey
            String rAesKey = FormatUtil.getAesKey(rDgtlEnvlp);

            String protocols = SecurityUtil.Base64Decode(SecurityUtil.AesDecrypt(returnData.get("protocols"), rAesKey));
            String[] protocolsArr = protocols.split(";");
            for (String s : protocolsArr) {
                if (userBank.equals(s.split("\\|")[2])) {
                    return s.split("\\|")[0];
                }
            }
        }
        return null;
    }
}
