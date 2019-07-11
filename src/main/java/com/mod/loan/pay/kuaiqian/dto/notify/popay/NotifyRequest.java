package com.mod.loan.pay.kuaiqian.dto.notify.popay;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * 报文实体
 * @author zan.liang
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)  
@XmlRootElement  
@XmlType(name = "notifyRequest", propOrder = {"notifyHead","notifyRequestBody"})  
public class NotifyRequest {

	@XmlElement(name = "notifyHead")  
	private NotifyHead notifyHead;
	
	@XmlElement(name = "notifyRequestBody")  
	private NotifyRequestBody notifyRequestBody;

	public NotifyHead getNotifyHead() {
		return notifyHead;
	}

	public void setNotifyHead(NotifyHead notifyHead) {
		this.notifyHead = notifyHead;
	}

	public NotifyRequestBody getNotifyRequestBody() {
		return notifyRequestBody;
	}

	public void setNotifyRequestBody(NotifyRequestBody notifyRequestBody) {
		this.notifyRequestBody = notifyRequestBody;
	}



	
}
