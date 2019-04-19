package com.mod.loan.itf.helipay.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.util.MD5;

public class HeliPayUtils {

	public static JSONObject requestMD5(String requrl, LinkedHashMap<String, String> params, String md5_key) {
		String sign = getSignMD5(params, md5_key);
		params.put("sign", sign);
		String resp = HttpClientService.getHttpResp(params, requrl);
		JSONObject result = JSON.parseObject(resp);
		return result;
	}

	public static JSONObject requestRSA(String requrl, LinkedHashMap<String, String> params, String rsa_key) {
		String sign = getSignRSA(params, rsa_key);
		params.put("sign", sign);
		String resp = HttpClientService.getHttpResp(params, requrl);
		JSONObject result = JSON.parseObject(resp);
		return result;
	}

	private static String getSignRSA(LinkedHashMap<String, String> params, String rsa_key) {
		String content = generateParams(params);
		try {
			return RSA.sign(rsa_key, content);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	private static String getSignMD5(LinkedHashMap<String, String> params, String md5_key) {
		String content = generateParams(params) + "&" + md5_key;
		return MD5.toMD5(content.toString());
	}

	private static String generateParams(LinkedHashMap<String, String> params) {
		StringBuffer content = new StringBuffer();
		List<String> keys = new ArrayList<String>(params.keySet());
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			String value = params.get(key);
			content.append("&" + value);
		}
		return content.toString();
	}

}
