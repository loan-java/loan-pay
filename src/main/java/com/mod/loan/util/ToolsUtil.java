package com.mod.loan.util;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: whw
 * @Date: 2019/5/8/008 12:11
 */
@Slf4j
public class ToolsUtil {

    //获取request的xml
    public static  String genRequestXml(HttpServletRequest httpRequest) {
        String line = null;
        ServletInputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        StringBuffer sb = new StringBuffer();
        try {
            is = httpRequest.getInputStream();
            isr = new InputStreamReader(is, "utf-8");
            br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            log.error("genRequestXml exception", e);
        } finally{
            try {
                if (null != is)is.close();
                if (null != isr)isr.close();
                if (null != br)br.close();
            } catch (Exception e) {
                log.error("io close exception", e);
            }
        }
        return sb.toString();
    }

    /**
     * 把xml 转为object
     *
     * @param xml
     * @return
     */
    public static Object xmlToObject(String xml) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes("UTF8"));
            @SuppressWarnings("resource")
            XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(in));
            return decoder.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map<String,String> xml2Map(String xml) {
        Map<String, String> map = new HashMap<String, String>();
        if(xml == null) return null;
        try {
            Document doc = DocumentHelper.parseText(xml);//将xml转为dom对象
            Element root = doc.getRootElement();//获取根节点
            List<Element> elements = root.elements();//获取这个子节点里面的所有子元素，也可以element.elements("userList")指定获取子元素
            for (Object obj : elements) {  //遍历子元素
                root = (Element) obj;
                map.put(root.getName(), root.getTextTrim());
                System.out.println(root.getName()+"--"+root.getTextTrim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    public static void main(String[] args) {
        Map<String,String> xml2Map = xml2Map("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<pay2BankNotify>" +
                "<membercode>10210084539</membercode>" +
                "<merchant_id>2190122155318e563b223182948080</merchant_id>" +
                "<apply_date>2019-01-22 15:53:19</apply_date>" +
                "<order_seq_id>3329358112</order_seq_id>" +
                "<fee>100</fee>" +
                "<status>111</status>" +
                "<error_code>0000</error_code>" +
                "<amt>50000</amt>" +
                "<bank>中国农业银行</bank>" +
                "<name>任盼盼</name>" +
                "<bank_card_no>6228480329273585573</bank_card_no>" +
                "<end_date>2019-01-22 15:53:21</end_date>" +
                "</pay2BankNotify>");

        System.out.println(xml2Map.toString());
    }

}
