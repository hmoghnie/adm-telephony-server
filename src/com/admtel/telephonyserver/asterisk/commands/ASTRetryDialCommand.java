package com.admtel.telephonyserver.asterisk.commands;

import com.admtel.telephonyserver.asterisk.ASTChannel;

public class ASTRetryDialCommand extends ASTCommand {
	private String destination;
	private long timeout=0;
	private String announce;
	private long sleep;
	private long loops;

	public ASTRetryDialCommand (ASTChannel channel, String announce, long sleep, long loops, String destination, long timeout){
		super(channel);
		this.destination = destination;
		this.timeout = timeout;
		this.announce = announce;
		this.sleep = sleep;
		this.loops = loops;
	}
	public String toString (){
		String actionId = channel.getId() + "___Dial";
		//Option g so that leg a doesn't hang up when leg b hangs up
		
		String dialStr = String.format("%s,%d,%d,%s,%d,gIi", announce, sleep, loops, destination, timeout);
		return String
						.format(
								"Action: AGI\nChannel: %s\nCommand: EXEC RETRYDIAL %s\nActionId: %s\nCommandID: %s",
								channel.getId(), dialStr, actionId, actionId);
	}
}
