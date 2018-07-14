package com.admtel.telephonyserver.commands;


import java.util.Collection;

import org.apache.mina.core.session.IoSession;
import org.naturalcli.ExecutionException;
import org.naturalcli.ICommandExecutor;
import org.naturalcli.ParseResult;

import com.admtel.telephonyserver.core.ScriptManager;
import com.admtel.telephonyserver.core.Script;
import com.admtel.telephonyserver.core.Switches;

public class CommandShowScriptsExecutor implements ICommandExecutor {
	IoSession session;
	public CommandShowScriptsExecutor(IoSession session){
		this.session = session;
		
	}
	@Override
	public void execute(ParseResult pr) throws ExecutionException {
		Collection<Script> scripts = ScriptManager.getInstance().getScripts();
		StringBuilder sb = new StringBuilder();
		sb.append("Scripts ");
		sb.append(scripts.size());
		sb.append("\n>\n");	
		for (Script script:scripts) {
			sb.append(script.dump());
			sb.append("\n");
		}
		sb.append("\n>");
		session.write(sb.toString());
	}
}
