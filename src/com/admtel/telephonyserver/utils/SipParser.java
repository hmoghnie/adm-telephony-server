package com.admtel.telephonyserver.utils;

public class SipParser {
	public static UriRecord parseUri(String uri){
		UriRecord result = new UriRecord();
		
		if (uri.startsWith("sip:")){
			uri = uri.substring(4);
		}
		else{
			return result;
		}
		
		int i = uri.indexOf("@");
		if (i== -1){
			i = uri.indexOf(":");
			if (i != -1){
				result.port = convertToSipPort(uri.substring(i+1));
				uri = uri.substring(0,i);
			}
			if (AdmUtils.validateIP(uri)) {
				result.host = uri;
			}
			else {
				result.username = uri;
			}
			return result;
		}
		String username=uri.substring(0, i);
		result.username = username;
		String s = uri.substring(i+1);
		i = s.indexOf(":");
		if (i == -1){
			result.host = s;
			result.port = 5060;
		}
		else{
			String host = s.substring(0, i);
			String port = s.substring(i+1);
			result.host = host;
			result.port = convertToSipPort(port);
		}
		return result;
	}
	public static UriRecord parseDialString(String dialString){
		UriRecord result = new UriRecord();	
		int i = dialString.indexOf("@");
		if (i== -1){
			result.username = dialString;
			return result;
		}
		String username=dialString.substring(0, i);
		result.username = username;
		String s = dialString.substring(i+1);
		i = s.indexOf(":");
		if (i == -1){
			result.host = s;
			result.port = 5060;
		}
		else{
			String host = s.substring(0, i);
			String port = s.substring(i+1);
			result.host = host;
			result.port = convertToSipPort(port);
		}
		return result;
	}
	public static int convertToSipPort(String p){
		int result = 5060;
		try
		{
			result = Integer.parseInt(p);
		}
		catch (Exception e){
			
		}
		return result;
	}
	public static void main(String[]args){		
		UriRecord r = parseUri("sip:hmoghnie$ProCaller@172.16.34.1:53199");
		System.out.println(r);
		r = parseUri("hmoghnie$ProCaller@172.16.34.1:53199");
		System.out.println(r);
		r = parseUri("sip:hmoghnie$ProCaller");
		System.out.println(r);
		r = parseUri("sip:hmoghnie$ProCaller@172.16.34.1");
		System.out.println(r);

		r = parseDialString("77329613820376@172.16.34.1:5062");
		System.out.println(r);
		r = parseUri("sip:31.193.4.168:5060");
		System.out.println("*************" + r.host);
	}
}
