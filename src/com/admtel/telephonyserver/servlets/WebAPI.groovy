package com.admtel.telephonyserver.servlets;
import org.mortbay.jetty.HttpStatus;

import com.admtel.telephonyserver.config.SystemConfig;
import com.admtel.telephonyserver.core.*;

import java.io.StringWriter;

import org.apache.log4j.Logger;

import com.admtel.telephonyserver.requests.*;
import com.admtel.telephonyserver.httpserver.HttpRequestMessage;
import com.admtel.telephonyserver.httpserver.HttpResponseMessage;

import groovy.xml.MarkupBuilder;

import com.admtel.telephonyserver.httpserver.AdmServlet;
import com.admtel.telephonyserver.interfaces.TokenSecurityProvider;
import com.admtel.telephonyserver.acd.*;
import com.admtel.telephonyserver.events.DisconnectCode;

import java.net.URLDecoder;
import java.security.PublicKey;
import com.admtel.telephonyserver.sessionlimiter.*;

import groovy.json.*;

class WebAPI extends AdmServlet {

	static Logger log = Logger.getLogger(WebAPI.class)


	public String securityKey

	public init(){
	}
	def index(request){
		"Welcome"
	}
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	def hangup(request){
		def result = API_Manager.instance.hangup(request.getParameter('channel'))		
		return result.toString()	
	}

	def conference_action(request){

		String action = request.getParameter('subAction')
		if (!action){
			return "invalid action"
		}
		if (action =="lock"){
			Result result = ConferenceManager.getInstance().lockConference(request.getParameter('conference'))
			return "{status:${result}}"
		}
		if (action =="unlock"){
			Result result = ConferenceManager.getInstance().unlockConference(request.getParameter('conference'))
			return "{status:${result}}"
		}
		""
	}
	//////////////////////////////////////////////////////////////////////////////////////////////
	def conference_participant_action(request){
		Conference c = ConferenceManager.getInstance().getConferenceById(request.getParameter('conference'))
		if (c == null){
			return "conference not found"
		}
		String action = request.getParameter('subAction')
		if (!action){
			return "invalid action"
		}
		Participant p = c.getParticipant(request.getParameter('participant'))
		if (p == null){
			return "Participant not found"
		}

		switch(action){
			case 'mute':
				API_Manager.instance.conferenceMute(p.getChannel().getUniqueId(), true)
				break;
			case 'unmute':
				API_Manager.instance.conferenceMute(p.getChannel().getUniqueId(), false)
				break;
			case 'deaf':
				API_Manager.instance.conferenceDeaf(p.getChannel().getUniqueId(), true)
				break;
			case 'undeaf':
				API_Manager.instance.conferenceDeaf(p.getChannel().getUniqueId(), false)
				break;
			case 'kick':
				API_Manager.instance.hangup(p.getChannel().getUniqueId())
				break;
		}
		""
	}
	def cps_get(request){
		List<Double> cps = SessionLimiterManager.getInstance().getCPS()
		if (cps.size() == 4){
			def json = new JsonBuilder([t1:cps.get(0), t2:cps.get(1), t3:cps.get(2), mean:cps.get(3)])
			return json.toString()
		}
		return ""
	}
	def cps_get_all(request){
		Map<String, List<Double>> result = SessionLimiterManager.getInstance().getCPS_All()
		def json = new JsonBuilder(result)
		return json.toString()
	}
	
	def limiter_set_ip(request){
		String ip = request.getParameter("ip")
		String cps_limit_str = request.getParameter("cps_limit")
		if (cps_limit_str){
			try{
				double cps_limit = Double.valueOf(cps_limit_str)
				IPLimiterDefinition ipLimiterDefinition = new IPLimiterDefinition(ip, cps_limit)
				SessionLimiterManager.getInstance().putIPLimiterDefinition(ipLimiterDefinition)
			}
			catch (Exception e){
				log.warn(e.message,e)
				return ""
			}
		}
		return "OK"
	}
	def limiter_remove_ip(request){
		String ip = request.getParameter("ip")
		SessionLimiterManager.getInstance().removeIPLimiterDefinition(ip)
		return "OK"
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	def conference_details(request){
		Conference c = ConferenceManager.getInstance().getConferenceById(request.getParameter('conference'))
		def writer = new StringWriter()
		if (c != null){
			List<Participant> p = c.getParticipants()
			def xml = new MarkupBuilder(writer)

			xml.'document'(type: "conference/xml") {
				conference(id:c.id){
					participants(){
						p.each{
							Channel channel = it.getChannel()
							participant(
									id:uniqueId,
									time:it.joinTime,
									caller:channel?.getCallingStationId(),
									memberId:it.memberId,
									talking:it.isTalking(),
									deaf:it.isDeaf(),
									moderator:it.isModerator(),
									muted:it.isMuted())
						}
					}
				}
			}
		}
		writer.toString()
	}
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	def dial(request){
		String destination = URLDecoder.decode(request.getParameter('destination'))
		String channel = request.getParameter('channel')
		int timeout = 10000
		if (request.getParameter('timeout')){
			timeout = Integer.valueOf(request.getParameter('timeout'))
		}

		API_Manager.instance.dial(channel, destination, timeout)
		"${channel} -> Dialed -> ${destination}"
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def join_conference(request){
		String channel = request.getParameter('channel')
		Channel c = Switches.getInstance().getChannelById(channel)
		if (c){
			String conferenceNumber = request.getParameter('conferenceNumber')
			if (!conferenceNumber)
				conferenceNumber = c.getUserData("conferenceNumber")
			API_Manager.instance.conferenceJoin(channel, conferenceNumber, false, false, false)
		}// TODO result code
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def get_channel_data(request){
		String channelId = request.getParameter('channel')
		String keyId = request.getParameter('key')
		Channel channel = Switches.getInstance().getChannelById(channelId)
		def jsonBuilder = JsonBuilder([key:keyId, value:channel?.getUserData(keyId)])
		jsonBuilder.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def set_channel_data(request){
		String channelId = request.getParameter('channel')
		String key = request.getParameter('key')
		String value = request.getParameter('value')
		Channel channel = Switches.getInstance().getChannelById(channelId)
		if (channel != null){
			channel.setUserData(key, value)
		}
		""
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def get_agent_channel(request){
		String agentId = request.getParameter('agent')
		AcdAgent tAgent= AcdManager.getInstance().getAgentById(agentId)
		def json = new JsonBuilder([agent:tAgent.getId(), channel:tAgent.getChannelId(), callChannel:tAgent.getCallChannelId()])
		return json.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def hangup_agent(request){
		String agentId = request.getParameter('agentId')
		AcdAgent tAgent = AcdManager.getInstance().getAgentById(agentId)
		if (tAgent != null){
			String channelId = tAgent.getChannelId()
			API_Manager.instance.hangup(channelId)
			return "Agent(${agentId}) hangup"
		}
		return "Agent ID ${agentId} not found"
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def get_agent_data(request){
		String agentName = request.getParameter('agent')
		String agentId = request.getParameter('agentId')
		String dataKey = request.getParameter('key')
		String requestId = request.getParameter('requestId')
		AcdAgent tAgent = null
		if (agentId)
			tAgent = AcdManager.getInstance().getAgentById(agentId)
		else if (agentName)
			tAgent = AcdManager.getInstance().getAgentByName(agentName)

		log.trace(tAgent.getCallChannelId())
		String t_message = "No Error"
		if (tAgent!=null){
			Channel channel = Switches.getInstance().getChannelById(tAgent.getCallChannelId())

			if (channel != null){
				if (channel.getUserData(dataKey) != null){
					def json = new JsonBuilder([request:requestId, message:"", status:0, key:dataKey, value:channel?.getUserData(dataKey)])
					return json.toString()
				}
				else{
					t_message = "Key ${dataKey} not found in channel ${channel.getUniqueId()}"
				}
			}
			else{
				t_message = "Channel not found"
			}
		}
		def json = new JsonBuilder([requestId:1234, message:t_message, status:-1])
		return json.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def set_agent_data(request){
		String agentName = request.getParameter('agent')
		String agentId = request.getParameter('agentId')
		String dataKey = request.getParameter('key')
		String requestId = request.getParameter('requestId')
		String dataValue = request.getParameter('value')
		AcdAgent tAgent = null
		if (agentId)
			tAgent = AcdManager.getInstance().getAgentById(agentId)
		else if (agentName)
			tAgent = AcdManager.getInstance().getAgentByName(agentName)
		log.trace(tAgent.getCallChannelId())
		String t_message = "No Error"
		if (tAgent!=null){
			Channel channel = Switches.getInstance().getChannelById(tAgent.getCallChannelId())

			if (channel){
				channel.setUserData(dataKey, dataValue)
				t_message = "value set"
			}
			else{
				t_message = "Channel not found"
			}
		}
		def json = new JsonBuilder([requestId:1234, message:t_message, status:-1])
		return json.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def agent_login(request){
		String agentName = request.getParameter('agent')
		AcdAgent agent = AcdManager.getInstance().getAgentByName(agentName)
		Switch _switch = Switches.getInstance().getRandom();
		String tMessage = "Invalid"
		if (agent != null){
			if (agent.getPassword().equals(request.getParameter('password'))){				
				def json = new JsonBuilder([requestId:1234, message:"", status:0, sipProxy:_switch.getDefinition().getSignallingIp(),
					sipUsername:agent.getName(), sipPassword:agent.getPassword(), sipSecure:false])
				return json.toString()
			}
			else{
				tMessage = "Agent ${agentName} Wrong password, entered ${request.getParameter('password')}, got ${agent.getPassword()}"
			}
		}
		else{
			tMessage = "agent ${agentName} not found"
		}
		def json = new JsonBuilder([requestId:1234, message:tMessage, status:-1])
		return json.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	def get_random_switch(request){
		Switch _switch = Switches.getInstance().getRandom()
		if (_switch){

			def params =  _switch.getDefinition().getParameters()
			params.put("requestId", "1234")
			params.put("message", "")
			params.put("id", _switch.getDefinition().getId())
			params.put("address", _switch.getDefinition().getAddress())

			def json = new JsonBuilder(params)
			return json.toString()
		}
		else{
			def json = new JsonBuilder([requestId:1234, message:"Switch not found", status:-1])
			return json.toString()
		}
	}
	///////////////////////////////////////////////////
	//Returns the switch that is hosting the given conference, if confernece is not found, return a random switch
	def get_conference_switch(request){
		Conference c = ConferenceManager.getConferenceById(request.getParameter('conference'))
		if (c){
			if (c.getSwitchId()){
				Switch _switch = Switches.getInstance().getById(c.getSwitchId())
				if (_switch){
					def json = new JsonBuilder([requestId:1234, message:"", id:_switch.getDefinition().getId(), address:_switch.getDefinition().getAddress()])
					return json.toString()
				}
				else{
					return get_random_switch(request)
				}
			}
		}
		else{
			return get_random_switch(request)
		}
			def json = new JsonBuilder([requestId:1234, message:"Switch not found", status:-1])
			return json.toString()
	}
	
	
	/*
	 * url format : webapi?action=get_switches
	 * 
	 *  return : json array of switch data
	 * */
	def get_switches(request) {
		Collection<Switch> switches = Switches.getInstance().getAll()
		
		def data = switches.collect{Switch s->
				[id:s.id, name:s.definition.name, address:s.definition.address, 
					type:s.definition.switchType, enabled:s.definition.enabled]
		}
		def builder = new JsonBuilder(data)
		builder.toString()
	}
	
	/*
	 * url format : webapi?action=get_channels&offset=<offset>&limit=<limit>
	 * 
	 * returns : json array of channel data and a count of total channels in the system
	 * */
	
	def get_channels(request) {
		Integer offset = 0
		Integer limit = 200
		if (request.getParameter("offset")) {
			offset = request.getParameter("offset").toInteger()
		}
		if (request.getParameter("limit")) {
			limit = request.getParameter("limit").toInteger()
		}
		List<Channel> tChannels = Switches.getInstance().getChannelsWithOffsetAndCount(offset, limit)
		def data = tChannels.collect{Channel c->
			[id:c.uniqueId, callingStationId:c.callingStationId, calledStationId:c.calledStationId,
				callOrigin:c.callOrigin, callState:c.callState, mediaState:c.mediaState, setupTime:c.setupTime?.toString(),
				answerTime:c.answerTime?.toString(), account:c.accountCode, script:c.script.toString()]
		}
		def t = [channels:data, count:Switches.getInstance().getChannelCount()]
		def builder = new JsonBuilder(t)
		builder.toString()
	}
	
	
	/*
	 * Issues a stop request on the server
	 * optional parameter force = true, will hangup all existing calls
	 * 
	 * url format : webapi?action=cmd_stop&force=<true|false>
	 * 
	 * */
	
	def cmd_stop(request) {
		String force = request.getParameter("force")
		if (force && force=="true") {
			Switches.getInstance().stop(true)
		}
		else {
			Switches.getInstance().stop(false)
		}
	}
	def cmd_reload_switches(request){
		Switches.getInstance().reload();
	}
	def cmd_prune_registered_user(request){
		Switches.getInstance().pruneRegisteredUser(request.getParameter("user"))
		""
	}

		
	/*
	 * Starts the server
	 * 
	 * url format : webapi?action=cmd_start
	 * */
	def cmd_start(request) {
		Switches.getInstance().start()
	}
	/*
	 * Reloads config file
	 * 
	 * url format : webapi?action=cmd_config_reload
	 * */
	def cmd_config_reload(request) {
		SystemConfig.getInstance().load();
	}

	def get_cps(request) {
		List<Double> result = SessionLimiterManager.getInstance().getCPS()
		def builder = new JsonBuilder(result)
		builder.toString()
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public void process(HttpRequestMessage request, HttpResponseMessage response){

		if (securityKey == request.getParameter('key')){
			def action = request.getParameter("action")
			if (!(action?.length()>0)){
				action = 'index'
			}
			try{
				log.trace("WebAPI received {"+request+"}");
				def model = "${action}"(request)
				log.trace("Return model is $model")
				response.appendBody(model)
				response.setResponseCode(HttpStatus.ORDINAL_200_OK)
			}
			catch (Exception e){
				log.fatal(e.getMessage(), e)
				response.setResponseCode(HttpStatus.Not_Implemented)
			}
		}
		else{
			response.appendBody("Unauthorized")
			response.setResponseCode(HttpStatus.ORDINAL_401_Unauthorized);
		}
	}	
}