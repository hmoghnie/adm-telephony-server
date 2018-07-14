package com.admtel.telephonyserver.events;

import com.admtel.telephonyserver.core.Channel;

public class DialEndedEvent extends ChannelEvent {


	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DialEndedEvent [dialedChannel=").append(dialedChannel)
				.append(", channel=").append(channel).append(", eventType=")
				.append(eventType).append("]");
		return builder.toString();
	}

	Channel dialedChannel;
	public DialEndedEvent(Channel channel, Channel dialedChannel) {
		super(channel);
		eventType = EventType.DialEnded;
		this.dialedChannel = dialedChannel;
	}

	public Channel getDialedChannel(){
		return dialedChannel;
	}
}
