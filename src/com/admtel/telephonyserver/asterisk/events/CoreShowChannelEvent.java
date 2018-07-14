package com.admtel.telephonyserver.asterisk.events;

import java.util.Map;

public class CoreShowChannelEvent extends ASTChannelEvent {

	public CoreShowChannelEvent(String switchId, Map<String, String> values) {
		super(switchId, values);
		eventType = EventType.CoreShowChannel;
	}

	/*
	 * Event: CoreShowChannel Channel: SIP/dmoghnie$ProCaller-00000004 UniqueID:
	 * 1433175753.4 Context: internal Extension: 9613820376 Priority: 1
	 * ChannelState: 6 ChannelStateDesc: Up Application: Dial ApplicationData:
	 * SIP/9613820376@bb9cbab8cbac4ed9a4326f389,60000,gIi CallerIDnum:
	 * 13106998711 CallerIDname: danny ConnectedLineNum: ConnectedLineName:
	 * Duration: 00:00:29
	 */
	public boolean isUp() {
		String channelState = values.get("ChannelStateDesc");
		return channelState != null && channelState.contains("Up");
	}

	public long getDuration() {
		try {
			String durationStr = values.get("Duration");
			if (durationStr != null) {
				durationStr = durationStr.trim();
				String[] d = durationStr.split(":");
				if (d.length == 3) {
					return 3600*Integer.parseInt(d[0])+60*Integer.parseInt(d[1])+Integer.parseInt(d[2]);
				}
			}
		} catch (Exception e) {

		}
		return 0;
	}
	public String getStateDesc(){
		return values.get("ChannelStateDesc");
	}
	public String getDurationStr(){
		return values.get("Duration");
	}
}
