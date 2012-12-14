/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.liveSense.misc.configloader;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * The <code>ContfigurationLoader</code> is the service
 * providing the following functionality:
 * <ul>
 * <li>Bundle listener to load initial felix configurations for bundles.
 * <li>Fires OSGi EventAdmin events on behalf of internal helper objects
 * </ul>
 **/

public class ConfigurationLoader implements SynchronousBundleListener, BundleActivator {

	private final static String CONFIGURATION_PROPERTY_NAME = "felix.configurationloader.name";

	ConfigurationAdmin configurationAdmin;
	ServiceMediator services;
	private final PersistencyManager persistence = new PersistencyManager();

	private final Set delayedBundles = new HashSet();

	// ---------- BundleListener -----------------------------------------------
	/**
	 * Loads and unloads any configuration provided by the bundle whose state
	 * changed. If the bundle has been started, the configuration is loaded. If
	 * the bundle is about to stop, the configurations are unloaded.
	 *
	 * @param event The <code>BundleEvent</code> representing the bundle state
	 *            change.
	 */
	@Override
	public void bundleChanged(BundleEvent event) {

		//
		// NOTE:
		// This is synchronous - take care to not block the system !!
		//

		switch (event.getType()) {
		case BundleEvent.STARTING:
			try {
				registerBundle(event.getBundle());
			} catch (Throwable t) {
				services.error(
						"bundleChanged: Problem loading initial configuration of bundle "
								+ event.getBundle().getSymbolicName() + " ("
								+ event.getBundle().getBundleId() + ")", t);
			} finally {
			}
			break;
		case BundleEvent.STOPPED:
			try {
				unregisterBundle(event.getBundle());
			} catch (Throwable t) {
				services.error(
						"bundleChanged: Problem unloading initial configuration of bundle "
								+ event.getBundle().getSymbolicName() + " ("
								+ event.getBundle().getBundleId() + ")", t);
			} finally {
			}
			break;
		}
	}


	@Override
	public void start(BundleContext context) throws Exception {

		services = new ServiceMediator(context);
		configurationAdmin = services.getConfigurationAdminService(ServiceMediator.NO_WAIT);
		context.addBundleListener(this);

		int ignored = 0;
		try {
			Bundle[] bundles = context.getBundles();
			for (int i=0;i<bundles.length; i++) {
				Bundle bundle = bundles[i];

				if ((bundle.getState() & (Bundle.ACTIVE)) != 0) {
					// load configurations from bundles which are ACTIVE
					try {
						registerBundle(bundle);
					} catch (Throwable t) {
						services.error(
								"Problem loading initial configuration of bundle "
										+ bundle.getSymbolicName() + " ("
										+ bundle.getBundleId() + ")", t);
					} finally {
					}
				} else {
					ignored++;
				}

				if ((bundle.getState() & (Bundle.ACTIVE)) == 0) {
					// remove configurations from bundles which are not ACTIVE
					try {
						unregisterBundle(bundle);
					} catch (Throwable t) {
						services.error(
								"Problem loading initial configuration of bundle "
										+ bundle.getSymbolicName() + " ("
										+ bundle.getBundleId() + ")", t);
					} finally {
					}
				} else {
					ignored++;
				}


			}
			services.debug(
					"Out of "+bundles.length+" bundles, "+ignored+" were not in a suitable state for initial config loading");
		} catch (Throwable t) {
			services.error("activate: Problem while loading initial configuration", t);
		} finally {
		}


	}

	@Override
	public void stop(BundleContext context) throws Exception {
		context.removeBundleListener(this);

		if (services != null) {
			services.deactivate();
			services = null;
		}
	}



	// ---------- Implementation helpers --------------------------------------
	/**
	 * Register a bundle and install the configurations included them.
	 *
	 * @param bundle
	 */
	public void registerBundle(final Bundle bundle) throws Exception {
		// if this is an update, we have to uninstall the old content first

		services.debug("Registering bundle "+bundle.getSymbolicName()+" for configuration loading.");

		registerBundleInternal(bundle);

		/*
        if (registerBundleInternal(bundle)) {

            // handle delayed bundles, might help now
            int currentSize = -1;
            for (int i = delayedBundles.size(); i > 0
                    && currentSize != delayedBundles.size()
                    && !delayedBundles.isEmpty(); i--) {

                Iterator di = delayedBundles.iterator();
                while (di.hasNext()) {
                    Bundle delayed = (Bundle)di.next();
                    if (registerBundleInternal(delayed)) {
                        di.remove();
                    }
                }
                currentSize = delayedBundles.size();
            }

        } else {
            // add to delayed bundles - if this is not an update!
            delayedBundles.add(bundle);
        }
		 */
	}



	private boolean registerBundleInternal(
			final Bundle bundle) throws Exception {


		// check if bundle has initial configuration
		final Iterator pathIter = PathEntry.getContentPaths(bundle);
		if (pathIter == null) {
			services.debug("Bundle "+bundle.getSymbolicName()+" has no initial configuration");
			return true;
		}

		while (pathIter.hasNext()) {
			PathEntry path = (PathEntry)pathIter.next();
			Enumeration entries = bundle.getEntryPaths(path.getPath());

			if (entries != null) {
				while (entries.hasMoreElements()) {
					URL url = bundle.getEntry((String)entries.nextElement());
					if (canHandle(url)) {
						install(url);
					}
				}
			}
		}

		return false;
	}

	/**
	 * Unregister a bundle. Remove installed content.
	 *
	 * @param bundle The bundle.
	 */
	public void unregisterBundle(final Bundle bundle) throws Exception {


		// check if bundle has initial configuration
		final Iterator pathIter = PathEntry.getContentPaths(bundle);
		if (pathIter == null) {
			services.debug("Bundle "+bundle.getSymbolicName()+" has no initial configuration");
			return;
		}

		// TODO : A boolean scr options if checked the configuration loaded by configurationloader is removed when the bundle contains remved. Now the configuuration stays untouched
		/*
        while (pathIter.hasNext()) {
            PathEntry path = (PathEntry)pathIter.next();
            Enumeration entries = bundle.getEntryPaths(path.getPath());

            if (entries != null) {
                while (entries.hasMoreElements()) {
                    URL url = bundle.getEntry((String)entries.nextElement());
                    if (canHandle(url)) {
                        uninstall(url);
                    }
                }
            }
        } */

	}


	public boolean canHandle(URL artifact)
	{
		return artifact.getFile().endsWith(".cfg") || artifact.getFile().endsWith(".config");
	}

	public void install(URL artifact) throws Exception
	{
		setConfig(artifact);
	}

	public void update(URL artifact) throws Exception
	{
		setConfig(artifact);
	}

	public void uninstall(URL artifact) throws Exception
	{
		deleteConfig(artifact);
	}

	private String getPidName(String pid, String factoryPid) {
		String suffix = factoryPid == null ? ".cfg" : "-" + factoryPid + ".cfg";
		return pid + suffix;
	}

	/**
	 * Set the configuration based on the config file.
	 *
	 * @param f
	 *            Configuration file
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	boolean setConfig(URL f) throws Exception
	{
		Properties p = new Properties();

		@SuppressWarnings("rawtypes")
		Dictionary ht = new Hashtable();

		InputStream in = new BufferedInputStream(f.openStream());
		try
		{
			// If the file name ends with .config, we using the Felix configuration format
			if (f.getFile().endsWith(".config")) {
				ht = ConfigurationHandler.read(in);
			} else {
				in.mark(1);
				boolean isXml = in.read() == '<';
				in.reset();
				if (isXml) {
					p.loadFromXML(in);
				} else {
					p.load(in);
				}
				((Hashtable)ht).putAll(p);
			}
		}
		finally
		{
			in.close();
		}

		// Searching for templated config entry.
		// If we found one we get Java System properties
		// named as the macros. The config became activated if that
		// system proprty is set.

		Pattern macros = Pattern.compile("\\$\\{(.*?)\\}");
		boolean valid = true;

		Enumeration enumr = ht.keys();
		while (enumr.hasMoreElements()) {
			Object key = enumr.nextElement();
			if (ht.get(key) instanceof String) {
				String str = (String)ht.get(key);
				if (str != null) {
					Matcher matcher = macros.matcher(str);

					HashSet<String> propNames = new HashSet<String>();
					while (matcher.find()) {
						propNames.add(matcher.group(1));
					}

					for (String prop : propNames) {
						String sysProp = System.getProperty(prop);
						if (sysProp == null) {
							valid = false;
						}
						if (valid) {
							str = StringUtils.replace(str, "${"+prop+"}", sysProp);
							//str = str.replaceAll("\\$\\{"+prop+"\\}", sysProp);
						}
					}
					if (valid) {
						ht.put(key, str);
					}
				}
			}
		}

		if (valid) {
			Util.performSubstitution(p);
			String pid[] = parsePid(getName(f.getFile()));
			ht.put(CONFIGURATION_PROPERTY_NAME, getPidName(pid[0], pid[1]));

			Configuration config = getConfiguration(pid[0], pid[1]);

			/*
			// Backuping parameters for restore
			String persistanceName = pid[0]+(pid[1] == null ? "" : "-" + pid[1]);
			if (config.getProperties() != null && config.getProperties().get(CONFIGURATION_PROPERTY_NAME) == null) {
				if (persistence.load(persistanceName).isEmpty()) {
					persistence.store(persistanceName, config.getProperties());
				}
			}
			 */
			if (config.getBundleLocation() != null) {
				config.setBundleLocation(null);
			}

			// If the configuration does not created by configuration loader we update it
			// In other cases (for example the user modified the loaded config) there is no configuration overwrite
			if (config.getProperties() == null || config.getProperties().get(CONFIGURATION_PROPERTY_NAME) == null || !config.getProperties().get(CONFIGURATION_PROPERTY_NAME).equals(getName(f.getFile()))) {
				config.update(ht);
			}
		}
		return true;
	}

	/**
	 * Remove the configuration.
	 *
	 * @param f
	 *            File where the configuration in whas defined.
	 * @return
	 * @throws Exception
	 */
	boolean deleteConfig(URL f) throws Exception
	{
		String pid[] = parsePid(getName(f.getFile()));
		Configuration config = getConfiguration(pid[0], pid[1]);
		config.delete();

		/*
        // Restore config if there is stored configuration presented
        String persistanceName = pid[0]+(pid[1] == null ? "" : "-" + pid[1]);

        Dictionary dict = persistence.load(persistanceName);
        if (!dict.isEmpty()) {
            config.update(dict);
            persistence.delete(persistanceName);
        }
		 */
		return true;
	}

	String[] parsePid(String path)
	{
		String pid = null;

		if (path.endsWith(".cfg")) {
			pid = path.substring(0, path.length() - 4);
		} else if (path.endsWith(".config")) {
			pid = path.substring(0, path.length() - 7);
		}

		int n = pid.indexOf('-');
		if (n > 0)
		{
			String factoryPid = pid.substring(n + 1);
			pid = pid.substring(0, n);
			return new String[]
					{
					pid, factoryPid
					};
		}
		else
		{
			return new String[]
					{
					pid, null
					};
		}
	}


	Configuration getConfiguration(String pid, String factoryPid)
			throws Exception
			{
		Configuration oldConfiguration = findExistingConfiguration(pid, factoryPid);
		if (oldConfiguration != null)
		{
			services.debug("Updating configuration from " + getPidName(pid, factoryPid));
			return oldConfiguration;
		}
		else
		{
			Configuration newConfiguration;
			if (factoryPid != null)
			{
				newConfiguration = configurationAdmin.createFactoryConfiguration(pid, null);
			}
			else
			{
				newConfiguration = configurationAdmin.getConfiguration(pid, null);
			}
			return newConfiguration;
		}
			}


	private Configuration findExistingConfiguration(String pid, String factoryPid) throws Exception
	{
		String filter = "(" + CONFIGURATION_PROPERTY_NAME + "=" + getPidName(pid, factoryPid) + ")";
		Configuration[] configurations = configurationAdmin.listConfigurations(filter);
		if (configurations != null && configurations.length > 0)
		{
			return configurations[0];
		}
		else
		{
			return null;
		}
	}

	/**
	 * Gets and decodes the name part of the <code>path</code>. The name is
	 * the part of the path after the last slash (or the complete path if no
	 * slash is contained). To support names containing unsupported characters
	 * such as colon (<code>:</code>), names may be URL encoded (see
	 * <code>java.net.URLEncoder</code>) using the <i>UTF-8</i> character
	 * encoding. In this case, this method decodes the name using the
	 * <code>java.netURLDecoder</code> class with the <i>UTF-8</i> character
	 * encoding.
	 *
	 * @param path The path from which to extract the name part.
	 * @return The URL decoded name part.
	 */
	private String getName(String path) {
		int lastSlash = path.lastIndexOf('/');
		String name = (lastSlash < 0) ? path : path.substring(lastSlash + 1);

		// check for encoded characters (%xx)
		// has encoded characters, need to decode
		if (name.indexOf('%') >= 0) {
			try {
				return URLDecoder.decode(name, "UTF-8");
			} catch (UnsupportedEncodingException uee) {
				// actually unexpected because UTF-8 is required by the spec
				services.error("Cannot decode "
						+ name
						+ " beause the platform has no support for UTF-8, using undecoded");
			} catch (Exception e) {
				// IllegalArgumentException or failure to decode
				services.error("Cannot decode " + name + ", using undecoded", e);
			}
		}

		// not encoded or problems decoding, return the name unmodified
		return name;
	}
}