package com.admtel.telephonyserver.scriptfactories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;

import com.admtel.telephonyserver.config.ConfigUtils;
import com.admtel.telephonyserver.core.Channel;
import com.admtel.telephonyserver.core.ChannelData;
import com.admtel.telephonyserver.core.Script;
import com.admtel.telephonyserver.core.SmartClassLoader;
import com.admtel.telephonyserver.interfaces.Loadable;
import com.admtel.telephonyserver.interfaces.ScriptFactory;

public class XMLScriptFactory implements ScriptFactory, Loadable {

	static Logger log = Logger.getLogger(XMLScriptFactory.class);

	private XMLConfiguration config;

	private List<ScriptData> scripts = new CopyOnWriteArrayList<ScriptData>();	 

	public XMLScriptFactory() throws ConfigurationException {
		config = new XMLConfiguration("scripts.xml");
		config.setReloadingStrategy(new FileChangedReloadingStrategy());
	}

	@Override
	public Script createScript(Channel channel) {
		Iterator<ScriptData> it = scripts.iterator();
		while (it.hasNext()) {
			ScriptData scriptData = it.next();
			log.debug("CreateScript, checking scriptData {" + scriptData+"}" );
			if (scriptData.context.equals(channel.getContext()) && scriptData.matches(channel.getChannelData().getCalledNumber())) {
				if (scriptData.className != null) {
					try {
						Script script = (Script) SmartClassLoader.createInstance(Script.class, scriptData.className);
						if (script != null) {
							log.debug(String.format(
									"Created script for (%s) - script (%s)",
									channel.getChannelData().toString(), scriptData));
							script.setParameters(scriptData.parameters);
						}
						return script;
					} catch (Exception e) {
						log.fatal(e.getMessage(), e);
					}
				}
			}
		}
		return null;
	}


	@Override
	public void load() {
		log.trace("Loading configuration ... ");
		config.reload();
		scripts.clear();
		int counter = 0;
		try {
			while (config.configurationAt(String.format("script(%d)", counter)) != null) {

				String name = config.getString(String.format(
						"script(%d)[@name]", counter));
				String called = config.getString(String.format(
						"script(%d).called", counter));
				String className = config.getString(String.format(
						"script(%d).class", counter));
				String context = config.getString(String.format("script(%d).context", counter), "internal");
				Map<String, String> parameters = new HashMap<String, String>();
				try{
				HierarchicalConfiguration parametersConfig = config
						.configurationAt(String.format("script(%d).parameters",
								counter));
				 parameters = ConfigUtils.loadParameters(parametersConfig);
				}
				catch (Exception e){
					log.warn(e.getMessage());
				}
				ScriptData sd = new ScriptData(name, called, className, context,
						parameters);
				scripts.add(sd);
				counter++;
				log.debug("Script definition loaded " + sd);
			}
		} catch (java.lang.IllegalArgumentException ae) {
			log.debug(ae.getMessage());
		} catch (Exception e) {
			log.warn(e.getMessage());
		}

	}

	@Override
	public void reload() {
		load();

	}

	@Override
	public void setParameters(Map<String, String> parameters) {
		// TODO Auto-generated method stub
		
	}

}
