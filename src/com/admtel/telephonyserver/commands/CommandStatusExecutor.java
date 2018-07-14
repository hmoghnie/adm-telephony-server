package com.admtel.telephonyserver.commands;

import java.text.NumberFormat;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.naturalcli.ExecutionException;
import org.naturalcli.ICommandExecutor;
import org.naturalcli.ParseResult;

import com.admtel.telephonyserver.core.ScriptManager;
import com.admtel.telephonyserver.core.Switches;
import com.admtel.telephonyserver.sessionlimiter.SessionLimiterManager;

public class CommandStatusExecutor implements ICommandExecutor {
	private IoSession session;

	public CommandStatusExecutor(IoSession session) {
		this.session = session;
	}

	@Override
	public void execute(ParseResult arg0) throws ExecutionException {
		Runtime runtime = Runtime.getRuntime();

		NumberFormat format = NumberFormat.getInstance();

		StringBuilder sb = new StringBuilder();
		long maxMemory = runtime.maxMemory();
		long allocatedMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		List<Double> cps = SessionLimiterManager.getInstance().getCPS();

		sb.append("\n>\n");
		sb.append("channels : " + Switches.getInstance().getChannelCount()
				+ "\n");
		sb.append("Scripts : " + ScriptManager.getInstance().getScriptsCount()
				+ "\n");
		sb.append("free memory: " + format.format(freeMemory / 1024) + "\n");
		sb.append("allocated memory: " + format.format(allocatedMemory / 1024)
				+ "\n");
		sb.append("max memory: " + format.format(maxMemory / 1024) + "\n");
		sb.append("total free memory: "
				+ format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024)
				+ "\n");
		sb.append("cps : ");
		if (cps.size() == 4) {			
			String cpsStr = String.format("%.2f/%.2f/%.2f/Mean(%.2f)", cps.get(0), cps.get(1), cps.get(2), cps.get(3));
			sb.append(cpsStr);
		} else {
			for (int i = 0; i < cps.size(); i++) {
				sb.append(cps.get(i));
				if (i < cps.size() - 1) {
					sb.append("/");
				}
			}
		}
		sb.append("\n>");
		session.write(sb.toString());
	}
}
