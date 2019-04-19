package com.mod.loan.itf.helipay.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class Base64Utils {
	public static String getBASE64(String s) {
		try {
			return Base64.getEncoder().encodeToString(s.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getBASE64(byte[] b) {
		return Base64.getEncoder().encodeToString(b);
	}

	// BASE64 编码的字符串 s 进行解码
	public static String getFromBASE64(String s) {
		try {

			byte[] b = Base64.getDecoder().decode(s);
			return new String(b, "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// BASE64 编码的字符串 s 进行解码
	public static byte[] getBytesBASE64(String s) {
		try {
			byte[] b = Base64.getDecoder().decode(s);
			return b;
		} catch (Exception e) {
			return null;
		}
	}
}
