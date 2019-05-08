package com.mod.loan.service.impl;

import com.alibaba.fastjson.JSON;
import com.bill99.asap.exception.CryptoException;
import com.bill99.asap.service.ICryptoService;
import com.bill99.asap.service.impl.CryptoServiceFactory;
import com.bill99.schema.asap.commons.Mpf;
import com.bill99.schema.asap.data.SealedData;
import com.bill99.schema.asap.data.UnsealedData;
import com.mod.loan.common.mapper.BaseServiceImpl;
import com.mod.loan.config.redis.RedisConst;
import com.mod.loan.config.redis.RedisMapper;
import com.mod.loan.kuaiqian.config.KuaiqianPayConfig;
import com.mod.loan.kuaiqian.dto.notify.popay.NotifyRequest;
import com.mod.loan.kuaiqian.dto.notify.popay.NotifyResponse;
import com.mod.loan.kuaiqian.util.CCSUtil;
import com.mod.loan.kuaiqian.util.PKIUtil;
import com.mod.loan.mapper.NotifyInfoMapper;
import com.mod.loan.model.NotifyInfo;
import com.mod.loan.service.NotifyInfoService;
import com.mod.loan.util.ToolsUtil;
import com.mod.loan.util.kuaiqian.entity.TransInfo;
import com.mod.loan.util.kuaiqian.util.ParseUtil;
import com.mod.loan.util.kuaiqian.util.SignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class NotifyInfoServiceImpl extends BaseServiceImpl<NotifyInfo, Long> implements NotifyInfoService {

    @Autowired
    private NotifyInfoMapper notifyInfoMapper;

    @Resource
    private KuaiqianPayConfig kuaiqianPayConfig;

    //字符编码
    private static String encoding = "UTF-8";

    /**
     * 自动支付回调
     * @param httpRequest
     * @return
     */
    @Override
    public String poPayNotifyCheck(HttpServletRequest httpRequest) {
        NotifyInfo notifyInfo = new NotifyInfo();
        //账户信息
        String membercode = kuaiqianPayConfig.getKuaiqianMemberCode();
        String fetureCode = kuaiqianPayConfig.getKuaiqianFetureCode();
        //接口版本
        String version = kuaiqianPayConfig.getKuaiqianVersion();
        //调用单笔快到银api2.0服务
        String responseXml = null;
        try {
            //获取客户端请求报文
            String xmlStrDecrypt = ToolsUtil.genRequestXml(httpRequest);
            log.info("[自动支付回调]返回xml解密前【" + xmlStrDecrypt + "】");
            NotifyRequest request = CCSUtil.converyToJavaBean(xmlStrDecrypt, NotifyRequest.class);
            String xmlStrEncryption = this.unsealxml(request,fetureCode,membercode);//解密请求报文
            Map<String,String>  map = ToolsUtil.xml2Map(xmlStrEncryption);
            if(map != null) {
                log.info("[自动支付回调]解密map："+ map.toString());
                notifyInfo.setType(2);
                notifyInfo.setAmount(map.get("amt") == null? BigDecimal.ZERO:new BigDecimal(map.get("amt")));
                notifyInfo.setOrderId(map.get("merchant_id"));
                notifyInfo.setStatus(map.get("status"));
                notifyInfo.setCreateTime(new Date());
            }
            notifyInfo.setXmlStrDecrypt(xmlStrDecrypt);
            notifyInfo.setXmlStrEncryption(xmlStrEncryption);
            int n = notifyInfoMapper.insert(notifyInfo);
            if(n == 0) {
                log.error("[自动支付回调]记录数据库失败：【" + xmlStrEncryption + "】");
            }
            //调用单笔快到银api2.0服务
            responseXml = this.sealxml(request.getNotifyRequestBody().getSealDataType().getOriginalData(),fetureCode,membercode,version);
        }catch (Exception e) {
            e.printStackTrace();
            log.error("[自动支付回调]出错：【" + e.getMessage() + "】");
        }
        return responseXml;
    }
    private String unsealxml(NotifyRequest request, String fetureCode, String membercode){
        SealedData sealedData = new SealedData();
        sealedData.setOriginalData(request.getNotifyRequestBody().getSealDataType().getOriginalData()==null?null: PKIUtil.utf8String2ByteWithBase64(request.getNotifyRequestBody().getSealDataType().getOriginalData()));
        sealedData.setSignedData(request.getNotifyRequestBody().getSealDataType().getSignedData()==null?null:PKIUtil.utf8String2ByteWithBase64(request.getNotifyRequestBody().getSealDataType().getSignedData()));
        sealedData.setEncryptedData(request.getNotifyRequestBody().getSealDataType().getEncryptedData()==null?null:PKIUtil.utf8String2ByteWithBase64(request.getNotifyRequestBody().getSealDataType().getEncryptedData()));
        sealedData.setDigitalEnvelope(request.getNotifyRequestBody().getSealDataType().getDigitalEnvelope()==null?null:PKIUtil.utf8String2ByteWithBase64(request.getNotifyRequestBody().getSealDataType().getDigitalEnvelope()));
        Mpf mpf = genMpf(fetureCode , membercode);
        UnsealedData unsealedData = null;
        try {
            ICryptoService service = CryptoServiceFactory.createCryptoService();
            unsealedData = service.unseal(mpf, sealedData);
        } catch (CryptoException e) {
            System.out.println(e);
        }
        byte[] decryptedData = unsealedData.getDecryptedData();
        String  rtnString = null;
        if (null != decryptedData) {
            rtnString = PKIUtil.byte2UTF8String(decryptedData);
            log.info("[自动支付回调]解密后返回报文 = " + rtnString);
        } else {
            rtnString = PKIUtil.byte2UTF8String(sealedData.getOriginalData());
            log.info("[自动支付回调]解密后返回报文 = " + rtnString);
        }
        return rtnString;
    }
    public static Mpf genMpf(String fetureCode, String membercode) {
        Mpf mpf = new Mpf();
        mpf.setFeatureCode(fetureCode);
        mpf.setMemberCode(membercode);
        return mpf;
    }
    private String sealxml(String ori, String fetureCode, String membercode, String version){
        Mpf mpf = genMpf(fetureCode , membercode);
        SealedData sealedData = null;
        try {
            ICryptoService service = CryptoServiceFactory.createCryptoService();
            sealedData = service.seal(mpf, ori.getBytes());
        } catch (CryptoException e) {
            System.out.println(e);
        }
        NotifyResponse response = CCSUtil.genResponse(membercode , version);
        byte[] nullbyte = {};
        byte[] byteOri = sealedData.getOriginalData() == null ? nullbyte : sealedData.getOriginalData();
        byte[] byteEnc = sealedData.getEncryptedData() == null ? nullbyte : sealedData.getEncryptedData();
        byte[] byteEnv = sealedData.getDigitalEnvelope() == null ? nullbyte : sealedData.getDigitalEnvelope();
        byte[] byteSig = sealedData.getSignedData() == null ? nullbyte : sealedData.getSignedData();
        response.getNotifyResponseBody().getSealDataType().setOriginalData(PKIUtil.byte2UTF8StringWithBase64(byteOri));
        //获取加签报文
        response.getNotifyResponseBody().getSealDataType().setSignedData(PKIUtil.byte2UTF8StringWithBase64(byteSig));
//		//获取加密报文
        response.getNotifyResponseBody().getSealDataType().setEncryptedData(PKIUtil.byte2UTF8StringWithBase64(byteEnc));
//		//数字信封
        response.getNotifyResponseBody().getSealDataType().setDigitalEnvelope(PKIUtil.byte2UTF8StringWithBase64(byteEnv));
        //请求报文
        String requestXml = CCSUtil.convertToXml(response, encoding);
        log.info("[自动支付回调]请求加密报文 = " + requestXml);
        return requestXml;
    }


    /**
     * 协议支付回调
     * @param httpRequest
     * @return
     */
    @Override
    public String cnpPayNotifyCheck(HttpServletRequest httpRequest) {
        NotifyInfo notifyInfo = new NotifyInfo();
        String orderId = "";
        //输出TR4
        StringBuffer tr4XML = new StringBuffer();
        try {
            //获取客户端请求报文
            String xmlStrEncryption = ToolsUtil.genRequestXml(httpRequest);
            log.info("[协议支付回调]返回xml【" + xmlStrEncryption + "】");
            notifyInfo.setXmlStrEncryption(xmlStrEncryption);
            TransInfo transInfo = new TransInfo();
            ParseUtil parseXML = new ParseUtil();
            if (SignUtil.veriSignForXml(xmlStrEncryption)) {
                //返回TR3后的第一个标志字段
                transInfo.setRecordeText_1("TxnMsgContent");
                //返回TR3后的错误标志字段
                transInfo.setRecordeText_2("ErrorMsgContent");
                //设置最后的解析方式
                transInfo.setFLAG(true);
                //开始接收TR3
                //将获取的数据传入DOM解析函数中
                HashMap respXml = parseXML.parseXML(xmlStrEncryption, transInfo);
                if (respXml != null) {
                    //交易类型编码（txnType）
                    String txnType = (String) respXml.get("txnType");
                    //交易金额（amount）
                    String amount = (String) respXml.get("amount");
                    //商户编号
                    String merchantId = (String) respXml.get("merchantId");
                    //终端编号（terminalId）
                    String terminalId = (String) respXml.get("terminalId");
                    //外部检索参考号（externalRefNumber）
                    String externalRefNumber = (String) respXml.get("externalRefNumber");
                    orderId  = externalRefNumber;
                    //检索参考号（refNumber）
                    String refNumber = (String) respXml.get("refNumber");
                    //应答码（responseCode）
                    String responseCode = (String) respXml.get("responseCode");

                    //TR3接收完毕
                    //当应答码responseCode的值为00时，交易成功 ,txnType :PUR是消费
                    if ("00".equals(responseCode)) {
                        //进行数据库的逻辑操作，比如更新数据库或插入记录。

                    }else{
                        log.error("[协议支付回调]交易失败：【" + xmlStrEncryption + "】");
                    }
                    tr4XML = new StringBuffer();
                    tr4XML.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><MasMessage xmlns=\"http://www.99bill.com/mas_cnp_merchant_interface\"><version>1.0</version><TxnMsgContent>");
                    tr4XML.append("<txnType>").append(txnType).append("</txnType>");
                    tr4XML.append("<interactiveStatus>TR4</interactiveStatus>");
                    tr4XML.append("<merchantId>").append(merchantId).append("</merchantId>");
                    tr4XML.append("<terminalId>").append(terminalId).append("</terminalId>");
                    tr4XML.append("<refNumber>").append(refNumber).append("</refNumber>");
                    tr4XML.append("</TxnMsgContent></MasMessage>");

                    notifyInfo.setType(1);
                    notifyInfo.setAmount(amount == null? BigDecimal.ZERO:new BigDecimal(amount));
                    notifyInfo.setOrderId(externalRefNumber);
                    notifyInfo.setStatus(responseCode);
                    notifyInfo.setCreateTime(new Date());
                }
                int n = notifyInfoMapper.insert(notifyInfo);
                if(n == 0) {
                    log.error("[协议支付回调]记录数据库失败：【" + xmlStrEncryption + "】");
                }
            }else{
                log.error("[协议支付回调]验签失败：【" + xmlStrEncryption + "】");
            }
        }catch (Exception e) {
            e.printStackTrace();
            log.error("[协议支付回调]出错：【" + e.getMessage() + "】");
        } finally {

        }
        return tr4XML.toString();
    }
}
