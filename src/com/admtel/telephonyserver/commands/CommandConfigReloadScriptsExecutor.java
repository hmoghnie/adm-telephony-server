package com.admtel.telephonyserver.commands;

import org.apache.mina.core.session.IoSession;
import org.naturalcli.ExecutionException;
import org.naturalcli.ICommandExecutor;
import org.naturalcli.ParseResult;

import com.admtel.telephonyserver.config.SystemConfig;
import com.admtel.telephonyserver.core.ScriptManager;
import com.admtel.telephonyserver.core.Switches;

public class CommandConfigReloadScriptsExecutor implements ICommandExecutor {
	IoSession session;
	public CommandConfigReloadScriptsExecutor(IoSession session){
		this.session = session;
		
	}
	@Override
	public void execute(ParseResult pr) throws ExecutionException {
		ScriptManager.getInstance().reload();
		session.write("Reloaded");
	}
}
