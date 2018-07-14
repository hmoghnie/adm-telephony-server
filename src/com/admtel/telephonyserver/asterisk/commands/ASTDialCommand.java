package com.admtel.telephonyserver.asterisk.commands;

import com.admtel.telephonyserver.asterisk.ASTChannel;

public class ASTDialCommand extends ASTCommand {
	private String destination;
	private long timeout=0;
	private long answerDelay=0;
	private boolean fakeRing = false;

	public ASTDialCommand (ASTChannel channel, String destination, long timeout, boolean fakeRing, long answerDelay){
		super(channel);
		this.destination = destination;
		this.timeout = timeout;
		this.answerDelay = answerDelay;
		this.fakeRing = fakeRing;
	}
	public String toString (){
		String actionId = channel.getId() + "___Dial";
		//Option g so that leg a doesn't hang up when leg b hangs up
		
		String dialStr;
		if (answerDelay >0){
			if (!fakeRing){
				dialStr = String.format("%s,%d,gIiZ(%d)", destination, timeout, answerDelay);
			}
			else{
				dialStr = String.format("%s,%d,rgIiZ(%d)", destination, timeout, answerDelay);
			}
		}
		else{
			if (!fakeRing){
				dialStr = String.format("%s,%d,gIi", destination, timeout);
			}
			else{
				dialStr = String.format("%s,%d,rgIi", destination, timeout);
			}
		}
		return String
						.format(
								"Action: AGI\nChannel: %s\nCommand: EXEC DIAL %s\nActionId: %s\nCommandID: %s",
								channel.getId(), dialStr, actionId, actionId);
	}
}
