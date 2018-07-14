package com.admtel.telephonyserver.events;

import java.util.ArrayList;
import java.util.List;

import com.admtel.telephonyserver.core.Channel;

public class ChannelListEvent extends Event {
	ArrayList<Channel> channels;
	public ChannelListEvent(List<Channel> channels){
		this.channels = new ArrayList<Channel>(channels.size());
		eventType = EventType.ChannelListed;
		this.channels.addAll(channels);
	}
}
