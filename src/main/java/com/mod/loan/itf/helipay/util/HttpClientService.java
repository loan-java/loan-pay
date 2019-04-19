package com.mod.loan.itf.helipay.util;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class HttpClientService {

	public static String getHttpResp(Map<String, String> reqMap, String httpUrl) {
		HttpClient client = new HttpClient();
		PostMethod method = new PostMethod(httpUrl);
		method.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
		method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
		method.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, new Integer(300000));
		String response = "";
		try {
			NameValuePair[] nvps = getNameValuePair(reqMap);
			method.setRequestBody(nvps);
			int rescode = client.executeMethod(method);
			if (rescode == HttpStatus.SC_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8"));
				String curline = "";
				while ((curline = reader.readLine()) != null) {
					response += curline;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			method.releaseConnection();
		}
		return response;
	}

	public static NameValuePair[] getNameValuePair(Map<String, String> bean) {
		List<NameValuePair> x = new ArrayList<NameValuePair>();
		for (Iterator<String> iterator = bean.keySet().iterator(); iterator.hasNext();) {
			String type = (String) iterator.next();
			x.add(new NameValuePair(type, String.valueOf(bean.get(type))));
		}
		Object[] y = x.toArray();
		NameValuePair[] n = new NameValuePair[y.length];
		System.arraycopy(y, 0, n, 0, y.length);
		return n;
	}
}
