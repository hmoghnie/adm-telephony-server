package com.admtel.telephonyserver.asterisk.events;

import java.util.Map;

public class ASTBridgeEvent extends ASTChannelEvent {

	public ASTBridgeEvent(String switchId, Map<String, String> values) {
		super(switchId, values);
		eventType = EventType.Bridge;
	}

	@Override
	public String getChannelId() {
		String channel = values.get("Channel1");
		return (channel==null?"":channel);
	}
	
	public String getPeerChannel(){
		String channel = values.get("Channel2");
		return (channel==null?"":channel);		
	}
	public String getCallerId1(){
		String callerId1 = values.get("CallerID1");
		return (callerId1 ==null?"":callerId1);
	}
	public String getCallerId2(){
		String callerId2 = values.get("CallerID2");
		return (callerId2 ==null?"":callerId2);
	}
}
