package com.admtel.telephonyserver.radius;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.attribute.VendorSpecificAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.dictionary.DictionaryParser;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusClient;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusUtil;

import com.admtel.telephonyserver.config.SystemConfig;
import com.admtel.telephonyserver.core.CallOrigin;
import com.admtel.telephonyserver.utils.AdmUtils;


public class RadiusClientAdapter {
	
	static Logger log = Logger.getLogger(RadiusClientAdapter.class);
	
	private static final String DICTIONARY_RESOURCE = "org/tinyradius/dictionary/default_dictionary";
	private static final String ADM_DICTIONARY = "com/admtel/telephonyserver/radius/adm_dictionary";
	private static final int VENDOR_SPECIFIC = 26;
	
	static Dictionary dictionary;

	RadiusClient radiusClient;
	
	static{
		InputStream s1 = RadiusServer.class.getClassLoader()
				.getResourceAsStream(DICTIONARY_RESOURCE);
		InputStream s2 = RadiusServer.class.getClassLoader()
				.getResourceAsStream(ADM_DICTIONARY);
		InputStream s = new SequenceInputStream(s1, s2);
		try {
			dictionary = DictionaryParser.parseDictionary(s);
		} catch (Exception e) {
			log.fatal("Failed to instantiate RadiusServer", e);
			dictionary = null;
		}
	}
	
	public RadiusClientAdapter(String host, String password, int authPort, int acctPort, int retryCount, int socketTimeoutMs) throws Exception{
		if (dictionary == null){
			log.fatal("Invalid dictionary file");
			throw new Exception ("Invalid Dictionary file");
		}
		radiusClient = new RadiusClient(host, password);
		radiusClient.setAcctPort(acctPort);
		radiusClient.setAuthPort(authPort);
		radiusClient.setRetryCount(retryCount);
		radiusClient.setSocketTimeout(socketTimeoutMs);
	}
	public void close(){
		if (radiusClient!=null){
			radiusClient.close();
		}
	}
	protected void apply(Map<String, Object> input, RadiusPacketDecorator radiusPacketDecorator){
		for (Map.Entry<String, Object> entry:input.entrySet()){
			String key = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof String){
				radiusPacketDecorator.addAttribute(key, (String)value);
			}
			else if (value instanceof List){
				List l = (List)value;
				for (Object o:l){
					radiusPacketDecorator.addAttribute(key, o.toString());
				}
			}
		}
		
	}
	public Map<String, Object> authorize(String username, String password, Map<String, Object> input){
		Map<String, Object> result = new HashMap<String, Object>();
		
		
		AccessRequest ar = new AccessRequest(username, password);
		RadiusPacketDecorator arDecorator = new RadiusPacketDecorator(ar);
		// ar.setAuthProtocol(AccessRequest.AUTH_PAP); // or AUTH_CHAP
		ar.setDictionary(dictionary);
		arDecorator.addAttribute("NAS-IP-Address",
				SystemConfig.getInstance().serverDefinition.getAddress());
		arDecorator.addAttribute("NAS-Port-Type", "Async");// TODO, set proper
		// value
		arDecorator.addAttribute("NAS-Port", "0"); //TODO set proper value

		apply(input, arDecorator);
		log.trace("Sending Radius Authorize message : " + ar+"\n\n");
		
		RadiusPacket response;
		try {
			response = radiusClient.authenticate(ar);
			response.setDictionary(dictionary);

			log.trace(response);
			switch (response.getPacketType()) {
			case RadiusPacket.ACCESS_ACCEPT: {

				List<VendorSpecificAttribute> attributes = response
						.getAttributes(VENDOR_SPECIFIC);
				for (VendorSpecificAttribute vendorSpecificAttribute : attributes) {
					List<RadiusAttribute> subAttributes = vendorSpecificAttribute
							.getSubAttributes();
					for (RadiusAttribute radiusAttribute : subAttributes) {
						// log.debug(attribute2.getAttributeTypeObject().getName()+"====="+RadiusUtil.getStringFromUtf8(attribute2.getAttributeData()));
						
						String radiusAttributeName = radiusAttribute.getAttributeTypeObject().getName();
						String radiusAttributeData = RadiusUtil.getStringFromUtf8(radiusAttribute
								.getAttributeData());
						String[] keyValue = radiusAttributeData.split("=");
						if (keyValue != null && keyValue.length == 2){
							radiusAttributeName = keyValue[0].trim();
							radiusAttributeData = keyValue[1].trim();
						}
						Object o = result.get(radiusAttributeName);
						if (o == null){
							result.put(radiusAttributeName, radiusAttributeData);
						}
						else if (o instanceof String){
							List<String> l = new LinkedList<String>();
							l.add((String)o);
							l.add(radiusAttributeData);
							result.put(radiusAttributeName, l);
							
						}
						else if(o instanceof List){
							List<String> l = (List<String>)o;
							l.add(radiusAttributeData);
						}
					}
				}
			}
			}
		}
		catch (Exception e){
			log.fatal(e.getMessage(), e);
		}
		return result;
	}
	
	public boolean accountingStop(String username, Map<String, Object>input){
		AccountingRequest acctRequest = new AccountingRequest(username, AccountingRequest.ACCT_STATUS_TYPE_STOP);
		RadiusPacketDecorator arDecorator = new RadiusPacketDecorator(
				acctRequest);

		acctRequest.setDictionary(dictionary);
		arDecorator.addAttribute("NAS-IP-Address",
				SystemConfig.getInstance().serverDefinition.getAddress());
		arDecorator.addAttribute("NAS-Port-Type", "Async");// TODO, set proper
		// value
		arDecorator.addAttribute("NAS-Port", "0"); //TODO set proper value

		apply(input, arDecorator);
		log.trace("Sending Accounting-Stop message : " + acctRequest+"\n\n");

		try {
			radiusClient.account(acctRequest);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		} catch (RadiusException e) {
			log.error(e.getMessage(), e);
		}
		return true;	
	}
}
