package com.admtel.telephonyserver.sessionlimiter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.admtel.telephonyserver.core.CallOrigin;
import com.admtel.telephonyserver.core.Channel;
import com.admtel.telephonyserver.core.EventsManager;
import com.admtel.telephonyserver.core.SmartClassLoader;
import com.admtel.telephonyserver.events.ChannelEvent;
import com.admtel.telephonyserver.events.Event;
import com.admtel.telephonyserver.interfaces.EventListener;
import com.admtel.telephonyserver.utils.AdmUtils;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class SessionLimiterManager implements EventListener {

	static final String NAME = "SESSION_LIMITER_MANAGER";
	static final String DAO_NAME = "#ip-limiter-dao";
	static final String GLOBAL_CPS_METER = "GLOBAL_CPS_METER";

	static Logger log = Logger.getLogger(SessionLimiterManager.class);
	MetricRegistry metrics = new MetricRegistry();

	private IPLimiterDAO ipLimiterDAO;
	private Map<String, IPLimiterDefinition> ipLimiterDefinitionsMap = new ConcurrentHashMap<String, IPLimiterDefinition>();

	private SessionLimiterManager() {

		EventsManager.getInstance().addEventListener(NAME, this);
		ipLimiterDAO = SmartClassLoader.getInstance().createInstanceI(
				IPLimiterDAO.class, DAO_NAME);
		if (ipLimiterDAO == null) {
			ipLimiterDAO = new IPLimiterDAOImpl(); //Default limiter implementation, returns empty list
		}
		this.loadIPLimiterDefinitions();
	}

	private static class SingletonHolder {
		private final static SessionLimiterManager instance = new SessionLimiterManager();
	}

	public static SessionLimiterManager getInstance() {
		return SingletonHolder.instance;
	}

	public void removeIPLimiterDefinition(String forIP) {
		ipLimiterDefinitionsMap.remove(forIP);
		metrics.remove(forIP);
		log.trace(String.format(
				"SessionLimiterManager::removeIPLimiterDefinition(%s)", forIP));
	}

	public void putIPLimiterDefinition(IPLimiterDefinition ipLimiterDefinition) {
		ipLimiterDefinitionsMap.put(ipLimiterDefinition.getIp(),
				ipLimiterDefinition);
		log.trace(String.format(
				"SessionLimiterManager::putIPLimiterDefinition(%s, %f)",
				ipLimiterDefinition.getIp(), ipLimiterDefinition.getLimit()));
	}

	public void loadIPLimiterDefinitions() {
		if (ipLimiterDAO != null) {
			List<IPLimiterDefinition> ipLimiterDefinitions = ipLimiterDAO
					.getDefinitions();
			Set<String> set = new HashSet<String>();
			for (IPLimiterDefinition ipLimiterDefinition : ipLimiterDefinitions) {
				log.trace(String.format(
						"Setting session limit to (%f) for ip (%s)",
						ipLimiterDefinition.getLimit(),
						ipLimiterDefinition.getIp()));
				ipLimiterDefinitionsMap.put(ipLimiterDefinition.getIp(),
						ipLimiterDefinition);
				set.add(ipLimiterDefinition.getIp());
			}
			Iterator<Map.Entry<String, IPLimiterDefinition>> it = ipLimiterDefinitionsMap
					.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, IPLimiterDefinition> entry = it.next();
				if (!set.contains(entry.getValue().getIp())) {
					it.remove();
					metrics.remove(entry.getValue().getIp());
				}
			}
		}
	}

	@Override
	public boolean onEvent(Event event) {
		switch (event.getEventType()) {
		// case Alerting:
		case Offered:
			ChannelEvent ce = (ChannelEvent) event;
			if (ce.getChannel().getCallOrigin() == CallOrigin.Inbound) {
				Meter globalCPS = metrics.meter(GLOBAL_CPS_METER);
				globalCPS.mark();
			}

			break;
		}

		return false;
	}

	public boolean canAcceptSessionFromIP(String ip) {

		IPLimiterDefinition ipLimiterDefinition = ipLimiterDefinitionsMap
				.get(ip);
		if (ipLimiterDefinition != null) {
			Meter meter = metrics.meter(ip);

			if (meter.getOneMinuteRate() < ipLimiterDefinition.getLimit()) {
				log.warn(String
						.format("Accepted canAcceptSessionFromIP session from (%s): current rate(%f) : max rate(%f)",
								ip, meter.getOneMinuteRate(),
								ipLimiterDefinition.getLimit()));
				meter.mark();
				return true;
			} else {
				log.warn(String
						.format("Refused canAcceptSessionFromIP session from (%s): current rate(%f) : max rate(%f)",
								ip, meter.getOneMinuteRate(),
								ipLimiterDefinition.getLimit()));
				return false;
			}
		}
		return true;
	}

	public List<Double> getCPS() {
		return getCPS(GLOBAL_CPS_METER);
	}

	public List<Double> getCPS(String forMeter) {
		Meter meter = metrics.meter(forMeter);
		List<Double> result = new ArrayList<Double>(4);
		result.add(AdmUtils.round(meter.getOneMinuteRate(), 2));
		result.add(AdmUtils.round(meter.getFiveMinuteRate(), 2));
		result.add(AdmUtils.round(meter.getFifteenMinuteRate(), 2));
		result.add(AdmUtils.round(getAverageCPS(meter), 2));
		return result;
	}

	public Map<String, List<Double>> getCPS_All() {
		Map<String, List<Double>> result = new HashMap<String, List<Double>>();
		for (String ip : metrics.getMeters().keySet()) {
			result.put(ip, getCPS(ip));
		}
		return result;
	}

	private double getAverageCPS(Meter meter) {
		return (meter.getOneMinuteRate() + meter.getFiveMinuteRate() + meter
				.getFifteenMinuteRate()) / 3.0;
	}
}
