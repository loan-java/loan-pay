
package com.mod.loan.controller;

import java.io.BufferedWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mod.loan.service.NotifyInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调接口
 */
@Slf4j
@RestController
@RequestMapping(value="/notify")
public class NotifyController{

	@Autowired
	private NotifyInfoService notifyInfoService;

	//字符编码
	private static String encoding = "UTF-8";

	/**
	 * 自动支付回调
	 * @param httpRequest
	 * @return
	 */
	@RequestMapping(value="/poPayNotify")
	public synchronized void poPayNotify(HttpServletRequest httpRequest,HttpServletResponse httpResponse) throws Exception {
        log.info("[自动支付回调]开始");
		String responseXml = notifyInfoService.poPayNotifyCheck(httpRequest);
		//返回响应报文
		httpResponse.setCharacterEncoding(encoding);
		httpResponse.setContentType(encoding);
		httpResponse.getWriter().write(responseXml);
		httpResponse.getWriter().flush();
        log.info("[自动支付回调]结束");
	}

	/**
	 * 协议支付回调
	 * @param httpRequest
	 * @return
	 */
	@RequestMapping("/cnpPayNotify")
	public synchronized void cnpPayNotify(HttpServletRequest httpRequest,HttpServletResponse httpResponse) throws Exception {
        log.info("[协议支付回调]开始");
		//设置请求信息的字符编码
		httpRequest.setCharacterEncoding(encoding);
		String responseXml = notifyInfoService.cnpPayNotifyCheck(httpRequest);
		//返回响应报文
		BufferedWriter outW = new BufferedWriter(httpResponse.getWriter());
		outW.write(responseXml);
		outW.flush();
		outW.close();
		log.info("[协议支付回调]结束");
	}

	
}
