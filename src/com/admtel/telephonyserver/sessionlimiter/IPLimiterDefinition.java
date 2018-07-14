package com.admtel.telephonyserver.sessionlimiter;

public class IPLimiterDefinition {
	String ip;
	Double limit;
	public String getIp() {
		return ip;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	public Double getLimit() {
		return limit;
	}
	public void setLimit(Double limit) {
		this.limit = limit;
	}
	public IPLimiterDefinition(String ip, Double limit) {
		super();
		this.ip = ip;
		this.limit = limit;
	}
	
}
