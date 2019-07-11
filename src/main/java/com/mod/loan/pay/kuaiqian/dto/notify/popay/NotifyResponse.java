package com.mod.loan.pay.kuaiqian.dto.notify.popay;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * 报文实体
 * @author zhiwei.ma
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)  
@XmlRootElement  
@XmlType(name = "notifyResponse", propOrder = {"notifyHead","notifyResponseBody"})  
public class NotifyResponse {

	@XmlElement(name = "notifyHead")  
	private NotifyHead notifyHead;
	
	@XmlElement(name = "notifyResponseBody")  
	private NotifyResponseBody notifyResponseBody;

	public NotifyHead getNotifyHead() {
		return notifyHead;
	}

	public void setNotifyHead(NotifyHead notifyHead) {
		this.notifyHead = notifyHead;
	}

	public NotifyResponseBody getNotifyResponseBody() {
		return notifyResponseBody;
	}

	public void setNotifyResponseBody(NotifyResponseBody notifyResponseBody) {
		this.notifyResponseBody = notifyResponseBody;
	}



	
}
