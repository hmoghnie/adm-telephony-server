package com.admtel.telephonyserver.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.LocaleUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.admtel.telephonyserver.acd.AcdManager;
import com.admtel.telephonyserver.config.SystemConfig;
import com.admtel.telephonyserver.core.Conference.ConferenceState;
import com.admtel.telephonyserver.core.Timers.Timer;
import com.admtel.telephonyserver.eventlisteners.SimpleEventListener;
import com.admtel.telephonyserver.events.ConferenceJoinedEvent;
import com.admtel.telephonyserver.events.ConferenceLeftEvent;
import com.admtel.telephonyserver.events.DestroyEvent;
import com.admtel.telephonyserver.events.DialFailedEvent;
import com.admtel.telephonyserver.events.DialStartedEvent;
import com.admtel.telephonyserver.events.DialStatus;
import com.admtel.telephonyserver.events.DisconnectCode;
import com.admtel.telephonyserver.events.DtmfEvent;
import com.admtel.telephonyserver.events.Event;
import com.admtel.telephonyserver.events.DisconnectedEvent;
import com.admtel.telephonyserver.events.AlertingEvent;
import com.admtel.telephonyserver.events.Event.EventType;
import com.admtel.telephonyserver.events.LinkedEvent;
import com.admtel.telephonyserver.freeswitch.FSChannel;
import com.admtel.telephonyserver.interfaces.EventListener;
import com.admtel.telephonyserver.interfaces.TimerNotifiable;
import com.admtel.telephonyserver.radius.RadiusServers;
import com.admtel.telephonyserver.registrar.UserLocation;
import com.admtel.telephonyserver.utils.AdmUtils;
import com.admtel.telephonyserver.utils.LimitedQueue;
import com.admtel.telephonyserver.utils.PromptsUtils;

public abstract class Channel implements TimerNotifiable {

	private static long MAX_CONNECT_TIME_MS = 86400000L;
	private static long CHANNEL_DESTROY_TIMER = 1000;
	public static Logger log = Logger.getLogger(Channel.class);

	public enum CallState {
		Null, Idle, Offered, Alerting, Accepted, Connected, Linked, Dialing, Disconnected, Dropped, Conferenced, AcdQueued, Queued,
	}

	public enum MediaState {
		PlayAndGetDigits, Idle, Playback,
	}

	private enum TimersDefs {
		HangupTimer, InterimUpdateTimer, DestroyTimer
	}

	Timer hangupTimer;
	Timer interimUpdateTimer;
	Timer destroyTimer;

	private List<EventListener> listeners = new CopyOnWriteArrayList<EventListener>();

	private CallState callState = CallState.Idle;
	private MediaState mediaState = MediaState.Idle;
	protected String id; // Switch based Id, might not be unique across switches
	protected String dtmfBuffer = "";
	protected Switch _switch;

	private String uniqueId;

	private ChannelData channelData = new ChannelData();
	private Map<String, String> userData = new HashMap<String, String>();

	protected DateTime createdTime = new DateTime();
	protected CallOrigin callOrigin = CallOrigin.Unknown;
	// Radius needed attributes
	protected DateTime setupTime = new DateTime();
	protected DateTime hangupTime;
	protected DateTime answerTime;
	protected DateTime ringTime;

	protected String h323CallOrigin;

	protected String acctUniqueSessionId;
	protected String acctSessionId;
	protected Integer h323DisconnectCause = 16;// normal call clearing
	protected String accountCode;

	protected String baseDirectory = SystemConfig.getInstance().serverDefinition
			.getBaseDirectory();

	protected Locale language;

	private String conferenceId;
	private Integer interimUpdateInterval = 0;// Default disable, in seconds
	private boolean sendAccountingStart = false;
	private boolean sendAccountingStop = false;
	
	protected Script script;

	public Script getScript() {
		return script;
	}

	public boolean isDetroyTimerOn() {
		return destroyTimer != null;
	}

	public void setScript(Script script) {
		this.script = script;
	}

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
	}

	public void setConferenceId(String conferenceId) {
		this.conferenceId = conferenceId;
	}

	private String memberId;
	private Result lastResult = Result.Ok;
	private Channel otherChannel = null;

	public Channel getOtherChannel() {
		return otherChannel;
	}

	public void setOtherChannel(Channel otherChannel) {
		this.otherChannel = otherChannel;
	}

	public String getUserData(String key) {
		return userData.get(key);
	}

	public void setUserData(String key, String value) {
		log.trace(String.format("{%s} %s = %s", this.getUniqueId(), key, value));
		userData.put(key, value);
	}

	public Map<String, String> getUserData() {
		return userData;
	}

	public void setUserData(Map<String, String> userData) {
		this.userData = userData;
	}

	private MessageHandler messageHandler = new QueuedMessageHandler() {

		@Override
		public void onMessage(Object message) {
			Channel.this.processNativeEvent(message);
		}

	};

	public Locale getLanguage() {
		return language;
	}

	public void setLanguage(Locale language) {
		this.language = language;
	}

	public void setLanguage(String language) {
		this.language = LocaleUtils.toLocale(language);
	}

	public void setHangupAfter(long msTimeout) {
		long t = msTimeout;
		if (msTimeout < 0 || msTimeout > MAX_CONNECT_TIME_MS) {
			t = MAX_CONNECT_TIME_MS;
		}
		hangupTimer = Timers.getInstance().startTimer(this, t, true,
				TimersDefs.HangupTimer);
	}

	public long getSessionTime() {
		if (answerTime == null)
			return 0;

		if (hangupTime != null) {
			return new Duration(answerTime, hangupTime).getStandardSeconds();
		}
		return new Duration(answerTime, new DateTime()).getStandardSeconds();
	}

	public Integer getInterimUpdateInterval() {
		return interimUpdateInterval;
	}

	public void setInterimUpdateInterval(Integer interimUpdateInterval) {
		this.interimUpdateInterval = interimUpdateInterval;
	}

	public boolean isSendAccountingStart() {
		return sendAccountingStart;
	}

	public void setSendAccountingStart(boolean sendAccountingStart) {
		this.sendAccountingStart = sendAccountingStart;
	}

	public boolean isSendAccountingStop() {
		return sendAccountingStop;
	}

	public void setSendAccountingStop(boolean sendAccountingStop) {
		this.sendAccountingStop = sendAccountingStop;
	}

	public String getServiceNumber() {
		return getChannelData().getServiceNumber();
	}

	public void setServiceNumber(String serviceNumber) {
		getChannelData().setServiceNumber(serviceNumber);
	}

	public CallOrigin getCallOrigin() {
		return callOrigin;
	}

	public void setCallOrigin(CallOrigin callOrigin) {
		this.callOrigin = callOrigin;
	}

	public DateTime getCreatedTime() {
		return createdTime;
	}

	public void setCreatedTime(DateTime createdTime) {
		this.createdTime = createdTime;
	}

	public String getUserName() {
		return getChannelData().getUserName();
	}

	public void setUserName(String userName) {
		getChannelData().setUserName(userName);
	}

	public DateTime getSetupTime() {
		return setupTime;
	}

	public void setSetupTime(DateTime setupTime) {
		this.setupTime = setupTime;
	}

	public DateTime getHangupTime() {
		return hangupTime;
	}

	public DateTime getRingTime() {
		return ringTime;
	}

	public void setRingTime(DateTime ringTime) {
		this.ringTime = ringTime;
	}

	public void setHangupTime(DateTime hangupTime) {
		this.hangupTime = hangupTime;
	}

	public String getH323CallOrigin() {
		return h323CallOrigin;
	}

	public void setH323CallOrigin(String h323CallOrigin) {
		this.h323CallOrigin = h323CallOrigin;
	}

	public String getH323RemoteAddress() {
		return getChannelData().getRemoteIP();
	}

	public String getAcctUniqueSessionId() {
		return acctUniqueSessionId;
	}

	public void setAcctUniqueSessionId(String acctUniqueSessionId) {
		this.acctUniqueSessionId = acctUniqueSessionId;
	}

	public String getAcctSessionId() {
		return this.acctSessionId;
	}

	public void setUniqueId(String uniqueId) {
		this.uniqueId = uniqueId;
	}

	public String getCalledStationId() {
		return getChannelData().getCalledNumber();
	}

	public String getCallingStationId() {
		return getChannelData().getCallerIdNumber();
	}

	public void addEventListener(EventListener listener) {
		if (!getListeners().contains(listener)) {
			getListeners().add(listener);
		}
	}

	public void removeEventListener(EventListener listener) {
		getListeners().remove(listener);
	}

	public void removeAllEventListeners() {
		getListeners().clear();
	}

	public Channel(Switch _switch, String id) {
		this._switch = _switch;
		this.id = id;
		this.uniqueId = UUID.randomUUID().toString();
		this.acctSessionId = UUID.randomUUID().toString();
		language = Locale.ENGLISH;
	}

	public String getUniqueId() {
		return uniqueId;
	}

	public Switch getSwitch() {
		return this._switch;
	}

	public void setSwitch(Switch _switch) {
		this._switch = _switch;
	}

	public void setDtmfBuffer(String dtmfBuffer) {
		this.dtmfBuffer = dtmfBuffer;
	}

	public String getSwitchId() {
		return _switch.getSwitchId();
	}

	public String getId() {
		return id;
	}

	public String getDtmfBuffer() {
		return dtmfBuffer;
	}

	public abstract Result internalPlayAndGetDigits(int max, String prompt,
			long timeout, String terminators, boolean interruptPlay);

	public abstract Result internalHangup(Integer integer);

	public abstract Result internalPlayback(String[] prompt, String terminators);

	public abstract Result internalPlayback(String prompt, String terminators);

	public abstract Result internalAnswer();

	public abstract Result internalPlayAndGetDigits(int max, String[] prompt,
			long timeout, String terminators, boolean interruptPlay);

	public abstract Result internalDial(String address, long timeout,
			boolean secure, boolean fakeRing, long answerDelay);

	public abstract Result internalDialWithRetry(String announce, long sleep,
			long loops, String address, long timeout);

	public abstract Result internalQueue(String queueName, boolean agent);

	public abstract String getContext();

	final public Result queue(String queueName, boolean agent) {
		// TODO add more parameters
		Result result = internalQueue(queueName, agent);
		return result;
	}

	final public Result acdQueue(String queueName) {
		// TODO add more parameters
		Result result = internalAcdQueue(queueName);
		return result;
	}

	public abstract Result internalAcdQueue(String queueName);

	private boolean isConnected() {
		return getCallState() == CallState.Connected;
	}

	private boolean isOffered() {
		return getCallState() == CallState.Offered;
	}

	private boolean isMediaActive() {
		return mediaState != MediaState.Idle;
	}

	final public Result playAndGetDigits(int max, String prompt, long timeout,
			String terminators) {
		log.trace(String.format("[%s] - playAndGetDigits(%d,%s,%d,%s)", this,
				max, prompt, timeout, terminators));
		if (!(isConnected() || isOffered())) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		if (isMediaActive()) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid media state", this));
			lastResult = Result.ChannelInvalidMediaState;
			return lastResult;
		}

		prompt = PromptsUtils.prepend(prompt, baseDirectory, "/sounds/",
				language.toString(), "/");
		lastResult = internalPlayAndGetDigits(max, prompt, timeout,
				terminators, true);

		if (lastResult == Result.Ok) {
			mediaState = MediaState.PlayAndGetDigits;
		}

		return lastResult;
	}

	final public Result playAndGetDigits(int max, String[] prompt,
			long timeout, String terminators) {
		log.trace(String.format("[%s] - playAndGetDigits(%d,%s,%d,%s)", this,
				max, prompt, timeout, terminators));
		if (!(isConnected() || isOffered())) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		if (isMediaActive()) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid media state", this));
			lastResult = Result.ChannelInvalidMediaState;
			return lastResult;
		}

		prompt = PromptsUtils.prepend(prompt, baseDirectory, "/sounds/",
				language.toString(), "/");
		lastResult = internalPlayAndGetDigits(max, prompt, timeout,
				terminators, true);

		if (lastResult == Result.Ok) {
			mediaState = MediaState.PlayAndGetDigits;
		}

		return lastResult;
	}

	final public Result hangup(DisconnectCode disconnectCode) {
		if (disconnectCode == null)
			disconnectCode = DisconnectCode.Normal;
		CallState callstate = getCallState();
		log.debug(String.format("[%s] - hangup (%s)", this, disconnectCode));
		if (callstate == CallState.Connected || callstate == CallState.Accepted
				|| callstate == CallState.Alerting
				|| callstate == CallState.Dialing
				|| callstate == CallState.Conferenced
				|| callstate == CallState.AcdQueued
				|| callstate == CallState.Idle || callstate == CallState.Linked
				|| callstate == CallState.Offered
				|| callstate == CallState.Queued) {
			lastResult = internalHangup(disconnectCode.toInteger());
			if (lastResult == Result.Ok) {
				setCallState(CallState.Dropped);
			}
		} else {
			log.trace(String.format("[%s] - hangup invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
		}
		log.debug("Result hangup End ( " + callstate + " ) disconnect code = "
				+ disconnectCode);
		return lastResult;
	}

	final public Result playback(String[] prompt, String terminators) {
		log.trace(String.format("[%s] - playback(%s,%s)", this, prompt,
				terminators));
		if (!(isConnected() || isOffered())) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		if (isMediaActive()) {
			log.warn(String.format("[%s], playback, invalid media state", this));
			lastResult = Result.ChannelInvalidMediaState;
			return lastResult;
		}

		prompt = PromptsUtils.prepend(prompt, baseDirectory, "/sounds/",
				language.toString(), "/");

		lastResult = internalPlayback(prompt, terminators);
		if (lastResult == Result.Ok) {
			mediaState = MediaState.Playback;
		}
		return lastResult;
	}

	final public Result playback(String prompt, String terminators) {
		log.trace(String.format("[%s] - playback(%s,%s)", this, prompt,
				terminators));
		if (!(isConnected() || isOffered())) {
			log.warn(String.format(
					"[%s], playAndGetDigits, invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		if (isMediaActive()) {
			log.warn(String.format("[%s], playback, invalid media state", this));
			lastResult = Result.ChannelInvalidMediaState;
			return lastResult;
		}
		prompt = PromptsUtils.prepend(prompt, baseDirectory, "/sounds/",
				language.toString(), "/");

		lastResult = internalPlayback(prompt, terminators);
		if (lastResult == Result.Ok) {
			mediaState = MediaState.Playback;
		}
		return lastResult;

	}

	final public Result answer() {
		log.trace(String.format("[%s] - answer", this));

		if (getCallState() == CallState.Offered) {
			lastResult = internalAnswer();
		} else {
			log.warn(String.format("[%s] - answer invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
		}
		if (lastResult == Result.Ok) {
			setCallState(CallState.Accepted);
		}
		return lastResult;
	}

	final public Result joinConference(String conferenceId, boolean moderator,
			boolean startMuted, boolean startDeaf) { // TODO add more parameters

		log.trace(String.format("[%s] - joinConference (%s, %s, %s, %s)", this,
				conferenceId, moderator, startMuted, startDeaf));

		if (getCallState() == CallState.Connected
				|| getCallState() == CallState.Linked) {
			lastResult = internalJoinConference(conferenceId, moderator,
					startMuted, startDeaf);
		} else {
			log.warn(String.format(
					"[%s] - joinConference - failed invalid call state", this));
			lastResult = Result.ChannelInvalidCallState;
		}
		if (lastResult == Result.Ok) {
			setCallState(CallState.Conferenced);
		}
		return lastResult;
	}

	public abstract Result internalJoinConference(String conferenceId,
			boolean moderator, boolean startMuted, boolean startDeaf);

	public abstract Result internalConferenceMute(String conferenceId,
			String memberId, boolean mute);

	final public Result conferenceMute(boolean mute) {
		log.trace(String.format("[%s] conferenceMute(%b)", this, mute));
		if (getCallState() != CallState.Conferenced) {
			log.warn(String.format("Channel (%s), invalid state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		lastResult = internalConferenceMute(conferenceId, memberId, mute);

		return lastResult;
	}

	public abstract Result internalConferenceDeaf(String conferenceId,
			String memberId, boolean deaf);

	final public Result conferenceDeaf(boolean deaf) {
		log.trace(String.format("[%s] conferenceDeaf(%b)", this, deaf));
		if (getCallState() != CallState.Conferenced) {
			log.warn(String.format("Channel (%s), invalid state", this));
			lastResult = Result.ChannelInvalidCallState;
			return lastResult;
		}
		lastResult = internalConferenceDeaf(conferenceId, memberId, deaf);

		return lastResult;
	}

	final public Result dial(String address, long timeout, long answerDelay) {
		return dial(address, timeout, false, false, answerDelay);
	}

	final public Result dial(String address, long timeout) {
		return dial(address, timeout, false, false, 0);
	}

	final public Result dial(String address, long timeout, boolean secure,
			boolean fakeRing) {
		return dial(address, timeout, secure, fakeRing, 0);
	}

	final public Result dial(String address, long timeout, boolean secure) {
		return dial(address, timeout, secure, false, 0);
	}

	final public Result dial(String address, long timeout, boolean secure,
			boolean fakeRing, long answerDelay) {

		log.trace(String.format("Channel(%s) dialing %s", this, address));

		if (getCallState() == CallState.Connected
				|| getCallState() == CallState.Accepted
				|| getCallState() == CallState.Offered
				|| getCallState() == CallState.AcdQueued) {
			String tAddress = address;
			AdmAddress admAddress = AdmAddress.fromString(tAddress);
			String translatedAddress = _switch.getAddressTranslator()
					.translate(admAddress);

			getChannelData().setDialedNumber(admAddress.destination);
			getChannelData().setDialedIP(admAddress.gateway);
			getChannelData().setDialedChannel(translatedAddress);

			if (translatedAddress == null) {
				log.warn(String.format(
						"Channel (%s) - invalid dial string (%s)", this,
						address));
				lastResult = Result.InvalidParameters;
				onEvent(new DialFailedEvent(this, DialStatus.InvalidNumber));
				return lastResult;
			}

			lastResult = internalDial(translatedAddress, timeout, secure,
					fakeRing, answerDelay);
			if (lastResult != Result.Ok) {
				onEvent(new DialFailedEvent(this, DialStatus.Unknown));
			}
		} else {
			log.warn(String.format("Channel (%s), invalid state", this));
			lastResult = Result.ChannelInvalidCallState;
			onEvent(new DialFailedEvent(this, DialStatus.InvalidState));
		}
		return lastResult;
	}

	final public Result dialWithRetry(String announce, long sleep, long loops,
			String address, long timeout) {

		log.trace(String.format("Channel(%s) dialing wtih retry %s", this,
				address));

		if (getCallState() == CallState.Connected
				|| getCallState() == CallState.Accepted
				|| getCallState() == CallState.Offered
				|| getCallState() == CallState.AcdQueued) {
			String tAddress = address;
			AdmAddress admAddress = AdmAddress.fromString(tAddress);
			String translatedAddress = _switch.getAddressTranslator()
					.translate(admAddress);

			getChannelData().setDialedNumber(admAddress.destination);
			getChannelData().setDialedIP(admAddress.gateway);
			getChannelData().setDialedChannel(translatedAddress);

			if (translatedAddress == null) {
				log.warn(String.format(
						"Channel (%s) - invalid dial string (%s)", this,
						address));
				lastResult = Result.InvalidParameters;
				onEvent(new DialFailedEvent(this, DialStatus.InvalidNumber));
				return lastResult;
			}

			lastResult = internalDialWithRetry(announce, sleep, loops,
					translatedAddress, timeout);
		} else {
			log.warn(String.format("Channel (%s), invalid state", this));
			lastResult = Result.ChannelInvalidCallState;
			onEvent(new DialFailedEvent(this, DialStatus.InvalidState));
		}
		return lastResult;
	}

	public Result getLastResult() {
		return lastResult;
	}

	public void setLastResult(Result lastResult) {
		this.lastResult = lastResult;
	}

	public boolean onEvent(Event e) {
		if (e == null)
			return true;

		// Add event for dump queue
		switch (e.getEventType()) {
		case DTMF: {
			DtmfEvent event = (DtmfEvent) e;
			dtmfBuffer += event.getDigit();
		}
			break;
		case PlaybackEnded:
			setMediaState(MediaState.Idle);
			break;
		case PlayAndGetDigitsEnded:
			setMediaState(MediaState.Idle);
			break;
		case AnswerFailed:
			setCallState(CallState.Connected);
			break;
		case PlaybackFailed:
			setMediaState(MediaState.Idle);
			break;
		case PlayAndGetDigitsFailed:
			setMediaState(MediaState.Idle);
			break;
		case QueueLeft:
			setCallState(CallState.Connected);
			break;
		case Offered:
			setCallState(CallState.Offered);
			ringTime = new DateTime();
			if (getAccountCode() == null) {
				setAccountCode(this.getUserName());
			}
			break;
		case Linked: {
			LinkedEvent le = (LinkedEvent) e;
			if (le.getPeerChannel() != null) {

				Channel dialingChannel = le.getChannel();
				Channel dialedChannel = le.getPeerChannel();

				this.getScript().addChannel(dialedChannel);
				dialedChannel.getChannelData().setDialedIP(
						dialingChannel.getChannelData().getDialedIP());
				dialedChannel.setCallingStationId(dialingChannel
						.getCallingStationId());
				String c = dialedChannel.getCalledStationId();
				if (c == null || c.isEmpty()) {
					dialedChannel.setCalledStationId(dialingChannel
							.getCalledStationId());
				}
				dialedChannel.setAcctUniqueSessionId(dialingChannel
						.getAcctUniqueSessionId());
				dialedChannel.setUserName(dialingChannel.getUserName());
				dialedChannel.getChannelData().setDestinationNumberIn(
						dialingChannel.getChannelData().getCalledNumber());
				dialedChannel.getChannelData().setRemoteIP(
						dialingChannel.getLoginIP());
				dialedChannel.setAccountCode(dialingChannel.getAccountCode());
				dialedChannel.setServiceNumber(dialingChannel
						.getServiceNumber());
				dialedChannel.setOtherChannel(dialingChannel);
				dialingChannel.setOtherChannel(dialedChannel);
			}

			setCallState(CallState.Linked);
		}
			break;
		case Unlinked:
			this.otherChannel = null;
			setCallState(CallState.Connected);
			break;
		case Connected:
			setCallState(CallState.Connected);
			setAnswerTime(new DateTime());
			if (interimUpdateInterval > 0) {
				sendInterimUpdate();
				interimUpdateTimer = Timers.getInstance().startTimer(this,
						interimUpdateInterval * 1000, false,
						TimersDefs.InterimUpdateTimer);
			}
			if (callOrigin == CallOrigin.Outbound && this.getScript() == null) {
				log.fatal(this + " : outbound answered and script is null");
			}

			break;
		case AcdQueueJoined:
			setCallState(CallState.AcdQueued);
			break;
		case DialStarted: {
			DialStartedEvent dse = (DialStartedEvent) e;
			if (dse.getDialedChannel() != null) {

				Channel dialingChannel = dse.getChannel();
				Channel dialedChannel = dse.getDialedChannel();

				if (dialedChannel.getCallState() == CallState.Connected) {
					log.warn(this
							+ " : received dial started while outbound channel is connected");
				}
				this.getScript().addChannel(dialedChannel);
				dialedChannel.getChannelData().setDialedIP(
						dialingChannel.getChannelData().getDialedIP());
				dialedChannel.setCallingStationId(dialingChannel
						.getCallingStationId());
				// dialedChannel.setCalledStationId(dialingChannel.getCalledStationId());
				dialedChannel.setAcctUniqueSessionId(dialingChannel
						.getAcctUniqueSessionId());
				dialedChannel.setUserName(dialingChannel.getUserName());
				dialedChannel.getChannelData().setDestinationNumberIn(
						dialingChannel.getChannelData().getCalledNumber());
				dialedChannel.getChannelData().setRemoteIP(
						dialingChannel.getLoginIP());
				dialedChannel.setAccountCode(dialingChannel.getAccountCode());
				dialedChannel.setServiceNumber(dialingChannel
						.getServiceNumber());
				dialedChannel.setOtherChannel(dialingChannel);
				dialingChannel.setOtherChannel(dialedChannel);
			} else {
				log.warn("DialStarted received but peer is null");
			}
		}
			break;
		case Alerting: {
			AlertingEvent ie = (AlertingEvent) e;
			setCallState(CallState.Alerting);
			ringTime = new DateTime();
		}
			break;
		case Disconnected: {
			DisconnectedEvent he = (DisconnectedEvent) e;
			hangupTime = new DateTime();
			// this.setOtherChannel(null);
			h323DisconnectCause = he.getDisconnectCode().toInteger();
			// destroyTimer = Timers.getInstance().startTimer(this,
			// CHANNEL_DESTROY_TIMER, true, TimersDefs.DestroyTimer);
		}
			break;
		case QueueJoined:
			setCallState(CallState.Queued);
			break;
		case ConferenceJoined: {
			ConferenceJoinedEvent cje = (ConferenceJoinedEvent) e;
			this.conferenceId = cje.getConferenceId();
			this.memberId = cje.getParticipantId();
			setCallState(CallState.Conferenced);
		}
			break;
		case ConferenceLeft: {
			this.conferenceId = null;
			this.memberId = null;
			setCallState(CallState.Connected);
		}
			break;
		}
		log.trace(String.format("onEvent: %s", e));

		try {
			Iterator<EventListener> it = getListeners().iterator();
			while (it.hasNext()) {
				it.next().onEvent(e);
			}
		} catch (Exception ex) {
			log.fatal(AdmUtils.getStackTrace(ex));
		}
		EventsManager.getInstance().onEvent(e);

		if (e.getEventType() == Event.EventType.Destroy) {

		}
		switch (e.getEventType()) {
		case Destroy:
			this.destroy();
			break;
		case Disconnected:
			this.onEvent(new DestroyEvent(this));
			break;
		}
		return false;
	}

	public void setCallingStationId(String callingStationId) {
		channelData.setCallerIdNumber(callingStationId);

	}

	public void setCalledStationId(String calledStationId) {
		channelData.setCalledNumber(calledStationId);

	}

	public MediaState getMediaState() {
		return mediaState;
	}

	public void setMediaState(MediaState mediaState) {
		this.mediaState = mediaState;
	}

	public Switch get_switch() {
		return _switch;
	}

	public void set_switch(Switch switch1) {
		_switch = switch1;
	}

	private void stopTimers() {
		Timers.getInstance().stopTimer(hangupTimer);
		Timers.getInstance().stopTimer(interimUpdateTimer);

	}

	public DateTime getAnswerTime() {
		return answerTime;
	}

	public void setAnswerTime(DateTime answerTime) {
		this.answerTime = answerTime;
	}

	@Override
	public String toString() {
		return "Channel ["
				+ (callState != null ? "callState=" + callState + ", " : "")
				+ (mediaState != null ? "mediaState=" + mediaState + ", " : "")
				+ (id != null ? "id=" + id + ", " : "")
				+ (uniqueId != null ? "uniqueId=" + uniqueId + ", " : "")
				+ (callOrigin != null ? "callOrigin=" + callOrigin + ", " : "")
				+ (hangupTimer != null ? "hangupTimer=" + hangupTimer + ", "
						: "") + (script != null ? "script=" + script : "")
				+ "]";
	}

	public String getDetailedDump() {
		final int maxLen = 20;
		return "Channel ["
				+ (callState != null ? "callState=" + callState + ", " : "")
				+ (mediaState != null ? "mediaState=" + mediaState + ", " : "")
				+ (id != null ? "id=" + id + ", " : "")
				+ (uniqueId != null ? "uniqueId=" + uniqueId + ", " : "")
				+ (channelData != null ? "channelData=" + channelData + ", "
						: "")
				+ (userData != null ? "userData="
						+ toString(userData.entrySet(), maxLen) + ", " : "")
				+ (createdTime != null ? "createdTime=" + createdTime + ", "
						: "")
				+ (callOrigin != null ? "callOrigin=" + callOrigin + ", " : "")
				+ (setupTime != null ? "setupTime=" + setupTime + ", " : "")
				+ (answerTime != null ? "answerTime=" + answerTime + ", " : "")

				+ (otherChannel != null ? "otherChannel=" + otherChannel : "")
				+ "]";
	}

	public String toCompactString() {
		return (uniqueId != null ? "id=" + uniqueId + ", " : "id=, ")
				+ (setupTime != null ? "setupTime=" + setupTime + ", "
						: "setupTime=, ")
				+ (ringTime != null ? "ringTime=" + ringTime + ", "
						: "ringTime=, ")
				+ (answerTime != null ? "answerTime=" + answerTime + ", "
						: "answerTime=, ")
				+ (id != null ? "channel=" + id + ", " : "id=, ")
				+ (_switch.getName() != null ? "switch=" + _switch.getName()
						+ ", " : "switch=, ")
				+ (callState != null ? "state=" + callState.name() + ", "
						: "state=, ")
				+ (this.getCalledStationId() != null ? "calledStationId="
						+ this.getCalledStationId() + ", "
						: "calledStationId=, ")
				+ (this.getCallingStationId() != null ? "callingStationId="
						+ this.getCallingStationId() : "callingStationId= ");
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
				&& i < maxLen; i++) {
			if (i > 0)
				builder.append("\n ");
			builder.append(iterator.next().getClass());
		}
		builder.append("]");
		return builder.toString();
	}

	public Integer getH323DisconnectCause() {
		return this.h323DisconnectCause;
	}

	public void setListeners(List<EventListener> listeners) {
		this.listeners = listeners;
	}

	public List<EventListener> getListeners() {
		return listeners;
	}

	public String getLoginIP() {
		return getChannelData().getLoginIP();
	}

	public void setLoginIP(String ip) {
		getChannelData().setLoginIP(ip);
	}

	public void setChannelData(ChannelData channelData) {
		this.channelData = channelData;
	}

	public ChannelData getChannelData() {
		return channelData;
	}

	@Override
	public boolean onTimer(Object data) {
		if (data instanceof TimersDefs) {
			TimersDefs td = (TimersDefs) data;
			switch (td) {
			case HangupTimer:
				hangup(DisconnectCode.Normal);
				break;
			case InterimUpdateTimer:
				sendInterimUpdate();
				return false; // Don't remove the timer

			case DestroyTimer:
				this.onEvent(new DestroyEvent(this));
				break;
			}
		}
		return true;
	}

	private void sendInterimUpdate() {
		RadiusServers.getInstance().accountingInterimUpdate(this);
	}

	public String getAccountCode() {
		return this.accountCode;
	}

	public void putMessage(Object message) {
		messageHandler.putMessage(message);
	}

	abstract protected void processNativeEvent(Object event);

	private void appendUserData(Map<String, String> userData2) {
		userData.putAll(userData2);

	}

	public void setCallState(CallState callState) {
		this.callState = callState;
	}

	public CallState getCallState() {
		return callState;
	}

	public String getConferenceId() {
		return conferenceId;
	}

	public String getData(String key) {
		return getChannelData().get(key);
	}

	public void setData(String key, String value) {
		getChannelData().put(key, value);
	}

	public void setAccountCode(String accountCode) {
		this.accountCode = accountCode;
	}

	abstract public void setSIP_URI_Options(String sipUriOptions);

	public void destroy() {
		stopTimers();
		removeAllEventListeners();
		_switch.removeChannel(this);
		if (this.script != null) {
			script.removeChannel(this);
		}
	}

	abstract public String dumpNativeEvents();
}
