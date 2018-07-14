package com.admtel.telephonyserver.interfaces;

import java.util.Map;

import com.admtel.telephonyserver.radius.AuthorizeResult;

public interface Authorizer {
	public AuthorizeResult authorize(String username,
			String password, String address, String serviceType,
			String calledStationId, String callingStationId, String loginIp, String serviceNumber, boolean routing, boolean number);
	public Map<String, Object> authorize(String username, String password, Map<String, Object> input);
}
