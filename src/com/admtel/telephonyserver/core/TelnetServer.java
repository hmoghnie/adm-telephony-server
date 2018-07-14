package com.admtel.telephonyserver.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.naturalcli.Command;
import org.naturalcli.InvalidSyntaxException;
import org.naturalcli.NaturalCLI;

import com.admtel.telephonyserver.commands.*;


public class TelnetServer extends IoHandlerAdapter{
	static Logger log = Logger.getLogger(TelnetServer.class);
	
	private static class SingletonHolder{
		private static final TelnetServer instance = new TelnetServer();
	}
	
	static public TelnetServer getInstance() {
		return SingletonHolder.instance;
	}
	
	private TelnetServer() {
		IoAcceptor acceptor = new NioSocketAcceptor();
        //acceptor.getFilterChain().addLast( "logger", new LoggingFilter() );
        acceptor.getFilterChain().addLast( "codec", new ProtocolCodecFilter( new TextLineCodecFactory( Charset.forName( "UTF-8" ))));
        acceptor.setHandler(  this );
        acceptor.getSessionConfig().setReadBufferSize( 2048 );
        acceptor.getSessionConfig().setIdleTime( IdleStatus.BOTH_IDLE, 10 );
        try {
			acceptor.bind( new InetSocketAddress("localhost", 7001) );
		} catch (IOException e) {
			log.fatal(e);
		}
        log.trace("Telnet Server Started");
	}
	@Override
    public void exceptionCaught( IoSession session, Throwable cause ) throws Exception
    {
        cause.printStackTrace();
    }
    @Override
    public void messageReceived( IoSession session, Object message ) throws Exception
    {
        String request = message.toString();
		NaturalCLI naturalCLI = buildNaturalCLI(session);
		if (naturalCLI != null){
			try{
				naturalCLI.execute(request);
			}
			catch (Exception e){
				log.warn(e);
			}
		}

    }
    @Override
    public void sessionIdle( IoSession session, IdleStatus status ) throws Exception
    {
    	//TODO, disconnect when session is idle ??
    }	
    
    static private NaturalCLI buildNaturalCLI(IoSession session) {
		Set<Command> cs = new HashSet<Command>();		

		try {
//			cs.add(new Command("date", "Shows the current date and time", new CommandShowDateExecutor (session)));
			cs.add(new Command("status", "Show server status", new CommandStatusExecutor(session)));
			cs.add(new Command("gc", "Garbage Collect", new CommandGarbageCollectExecutor(session)));
			cs.add(new Command("bye", "Close this connection", new CommandByeExecutor(session)));
			cs.add(new Command("show switches", "Show switches", new CommandShowSwitchesExecutor(session)));
			cs.add(new Command("show channels", "Show Channels", new CommandShowChannelsExecutor(session)));
			cs.add(new Command("show threads", "Show threads", new CommandThreadStackExecutor(session)));
			cs.add(new Command("show scripts", "Show scripts", new CommandShowScriptsExecutor(session)));
			cs.add(new Command("start", "Start", new CommandStartExecutor(session)));
			cs.add(new Command("stop [<now:string>]", "Stop [now]", new CommandStopExecutor(session)));
			cs.add(new Command("config reload","Reload system configuration", new CommandConfigReloadExecutor(session)));
			cs.add(new Command("config reload scripts","Reload script definitions", new CommandConfigReloadScriptsExecutor(session)));
			cs.add(new Command("set log <level:string>","Set log level <TRACE|DEBUG|ALL|WARN|FATAL>",  new CommandSetLogExecutor(session)));
		
			Command helpCommand = new Command("help", "Show this help", new CommandHelpExecutor(session, cs));
			cs.add(helpCommand);
		} catch (InvalidSyntaxException e) {
			log.warn(e, e);
			return null;
		}		
		
		return new NaturalCLI(cs);	
    }
}
