package com.admtel.telephonyserver.asterisk.commands;

import com.admtel.telephonyserver.asterisk.ASTChannel;

public class ASTProgressCommand extends ASTCommand {

	public ASTProgressCommand (ASTChannel channel){
		super(channel);
	}
	public String toString (){
		String actionId = channel.getId() + "___Progress";
		
		return String
						.format(
								"Action: AGI\nChannel: %s\nCommand: EXEC PROGRESS\nActionId: %s\nCommandID: %s",
								channel.getId(), actionId, actionId);
	}
}
