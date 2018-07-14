package com.admtel.telephonyserver.commands;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.naturalcli.ExecutionException;
import org.naturalcli.ICommandExecutor;
import org.naturalcli.ParseResult;

public class CommandSetLogExecutor implements ICommandExecutor {
	IoSession session;
	public CommandSetLogExecutor(IoSession session){
		this.session = session;
		
	}
	@Override
	public void execute(ParseResult pr) throws ExecutionException {
		StringBuilder sb = new StringBuilder();
		sb.append("\n>\n");
		String levelStr = (String) pr.getParameterValue(0);
		if (levelStr != null){
			try{
				Level level = Level.toLevel(levelStr.toUpperCase());
				Logger root = Logger.getLogger("com.admtel");
				root.setLevel(level);
				sb.append("Setting log level to ").append(levelStr).append("\n");
			}
			catch (Exception e){
				sb.append("Failed to set logging level");
			}
		}
		sb.append("\n>");
		session.write(sb.toString());
	}
}
