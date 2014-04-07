package com.gentics.cr.template;

import com.gentics.cr.exceptions.CRException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;
import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;

import com.gentics.cr.util.CRUtil;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * 
 * Last changed: $Date: 2010-04-01 15:25:54 +0200 (Do, 01 Apr 2010) $
 * 
 * @version $Revision: 545 $
 * @author $Author: supnig@constantinopel.at $
 * 
 */
public class VelocityTemplateManagerFactory {

	private static Logger log = Logger.getLogger(VelocityTemplateManagerFactory.class);

	private static final String VELOCITYMACRO_FILENAME = "velocitymacros.vm";

	private static boolean configured = false;

	private static JCS cache;

	/**
	 * Get a configured VelocityTemplateManager.
	 * 
	 * @param encoding
	 *            if null defaults to utf-8
	 * @param macropath
	 * @return {@link com.gentics.cr.template.VelocityTemplateManagerFactory #getConfiguredVelocityTemplateManagerInstance(String, String, String)}
	 * @throws Exception
	 */
	public static synchronized VelocityTemplateManager getConfiguredVelocityTemplateManagerInstance(String encoding,
			String macropath) throws Exception {
		return VelocityTemplateManagerFactory.getConfiguredVelocityTemplateManagerInstance(encoding, macropath, "");

	}

	/**
	 * Get a configured VelocityTemplateManager.
	 * 
	 * @param encoding
	 *            if null defaults to utf-8
	 * @param macropath
	 * @param propFile
	 * @return new instance of
	 *         {@link com.gentics.cr.template.VelocityTemplateManager} with the
	 *         specified encoding.
	 * @throws Exception
	 */
	public static synchronized VelocityTemplateManager getConfiguredVelocityTemplateManagerInstance(String encoding,
			String macropath, String propFile) throws Exception {
		if (encoding == null) {
			encoding = "utf-8";
		}
		if (!configured) {
			configure(encoding, macropath, propFile);
			configured = true;
		}
		return (new VelocityTemplateManager(encoding));

	}

	/**
	 * Create a Velocity template with the given name and source.
	 * 
	 * Attention: name of template must be unique!!
	 * 
	 * @param name
	 * @param source
	 * @param encoding
	 *            encoding as string or null => defaults to utf-8
	 * @return template (either a cached one, found using the key: name + source
	 *         or a newly created one).
	 */
	public static Template getTemplate(String name, String source, String encoding) {
		if (encoding == null) {
			encoding = "utf-8";
		}

		if (cache != null) {
			try {
				cache = JCS.getInstance("gentics-cr-velocitytemplates");
				log.debug("Initialized cache zone for \"gentics-cr-velocitytemplates\".");

			} catch (CacheException e) {

				log.warn("Could not initialize Cache for Velocity templates.", e);
			}
		}
		
		VelocityTemplateWrapper wrapper = null;

		if (cache != null) {
			// removed source from cache key because name should be unique.
			wrapper = (VelocityTemplateWrapper) cache.get(name);
		}

		if (wrapper == null) {

			StringResourceRepository rep = StringResourceLoader.getRepository();

			if (rep == null) {
				rep = new StringResourceRepositoryImpl();
				StringResourceLoader.setRepository(StringResourceLoader.REPOSITORY_NAME_DEFAULT, rep);
			}

			rep.setEncoding(encoding);
			rep.putStringResource(name, source);

			try {

				wrapper = new VelocityTemplateWrapper(Velocity.getTemplate(name));

			} catch (ResourceNotFoundException e1) {
				log.warn("Could not create Velocity Template.", e1);
			} catch (ParseErrorException e1) {
				log.warn("Could not create Velocity Template.", e1);
			} catch (Exception e1) {
				log.warn("Could not create Velocity Template.", e1);
			}
			rep.removeStringResource(name);
			if (cache != null) {
				try {
					cache.put(name, wrapper);
				} catch (CacheException e) {
					log.warn("Could not put Velocity Template to cache.", e);
				}
			}
		}
		return (wrapper.getTemplate());
	}

	private static void configure(String encoding, String macropath) throws Exception {
		configure(encoding, macropath, "");
	}

	private static void configure(String encoding, String macropath, String propFile) throws Exception {
		Properties props = new Properties();

		// no file with properties given, set default properties
		if (CRUtil.isEmpty(propFile)) {
			props.setProperty("string.loader.description", "String Resource Loader");
			props.setProperty("string.resource.loader.class",
					"org.apache.velocity.runtime.resource.loader.StringResourceLoader");
			props.setProperty("resource.loader", "file,string");

			if (macropath != null) {
			}

			// if a properties file is given, use this one to set the
			// vtl-properties
		} else {
			try {
				FileInputStream fis = new FileInputStream(CRUtil.resolveSystemProperties(propFile));
				props.load(fis);
				fis.close();
			} catch (FileNotFoundException e) {
				log.error("The velocity-properties file \"" + propFile + "\" does not exist!");
			}
		}

		if (macropath != null) {
			// Configure file resource loader when we have to load velocimacro
			// library
			// TODO: load file resource loader when no confpath is given to
			// allow the users to include their templates in runtime
			if (!props.containsKey("file.loader.description")) {
				props.setProperty("file.loader.description", "File Resource Loader");
			}
			if (!props.containsKey("file.resource.loader.class")) {
				props.setProperty("file.resource.loader.class",
						"org.apache.velocity.runtime.resource.loader.FileResourceLoader");
			}

			if (!props.containsKey("resource.loader")) {
				props.setProperty("resource.loader", "string,file");
			}

			if (!props.containsKey("file.resource.loader.path")) {
				props.setProperty("file.resource.loader.path", macropath);
			}

			// This property, with possible values of true or false, defaulting
			// to false, controls if Velocimacros defined inline are 'visible'
			// only to the defining template. In other words, with this property
			// set to true, a template can define inline VMs that are usable
			// only by the defining template. You can use this for fancy VM
			// tricks - if a global VM calls another global VM, with inline
			// scope, a template can define a private implementation of the
			// second VM that will be called by the first VM when invoked by
			// that template. All other templates are unaffected.
			props.setProperty("velocimacro.permissions.allow.inline.local.scope", "true");

			if (!props.containsKey("velocimacro.library")) {
				try {

					File macroFile = new File(macropath + VELOCITYMACRO_FILENAME);
					log.debug("Trying to create a macrofile for velocity in " + macropath + VELOCITYMACRO_FILENAME);
					macroFile.createNewFile();

					// TODO: autodetect velocimacro library
					// using *.vm files in confpath
					props.setProperty("velocimacro.library", VELOCITYMACRO_FILENAME);
				} catch (IOException e) {
					log.error("Could not find or create macro file " + "for velocity template manager.", e);
				}
			}
		}

		// Configure Log4J logging for velocity
		props.put("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
		props.put("runtime.log.logsystem.log4j.category", "org.apache.velocity");

		props.put("input.encoding", encoding);
		props.put("output.encoding", encoding);

		Velocity.init(props);
	}
}
