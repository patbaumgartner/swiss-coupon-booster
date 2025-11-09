package com.patbaumgartner.couponbooster.coop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * Injects advanced stealth JavaScript to evade DataDome and other bot detection systems.
 * <p>
 * This component loads a comprehensive JavaScript snippet that modifies various browser
 * properties and APIs to make automated browsing indistinguishable from real user
 * behavior. It targets multiple detection vectors including:
 * <ul>
 * <li>navigator.webdriver property</li>
 * <li>Plugin enumeration</li>
 * <li>Hardware characteristics</li>
 * <li>Canvas fingerprinting</li>
 * <li>Chrome runtime detection</li>
 * <li>IFrame consistency</li>
 * </ul>
 *
 * @see CoopAuthenticationService
 */
@Component
public class DatadomeStealthInjector {

	private static final Logger log = LoggerFactory.getLogger(DatadomeStealthInjector.class);

	/**
	 * Minimal fallback script if the external file cannot be loaded. This provides basic
	 * webdriver hiding functionality.
	 */
	private static final String FALLBACK_SCRIPT = """
			(function() {
			    'use strict';
			    // Minimal stealth - hide navigator.webdriver
			    if (navigator.webdriver !== undefined) {
			        Object.defineProperty(navigator, 'webdriver', {
			            get: () => undefined,
			            configurable: true
			        });
			    }
			    console.log('⚠️ Minimal stealth mode (fallback)');
			})();
			""";

	/**
	 * Returns the JavaScript code needed to evade bot detection systems.
	 * <p>
	 * This method first attempts to load the comprehensive stealth script from the
	 * classpath resource {@code datadome-stealth.js}. If the resource cannot be loaded,
	 * it falls back to a minimal script that only hides the navigator.webdriver property.
	 * <p>
	 * The script should be injected using {@code page.addInitScript()} or
	 * {@code context.addInitScript()} to ensure it runs before any page content loads.
	 * @return JavaScript code as a string to be injected into the browser context
	 */
	public String getStealthScript() {
		try {
			var scriptResource = new ClassPathResource("datadome-stealth.js");
			String script = StreamUtils.copyToString(scriptResource.getInputStream(), StandardCharsets.UTF_8);

			if (log.isDebugEnabled()) {
				log.debug("Loaded DataDome stealth script: {} characters", script.length());
			}

			return script;
		}
		catch (Exception exception) {
			log.error("Could not load external DataDome stealth script, using minimal fallback: {}",
					exception.getMessage(), exception);
			return FALLBACK_SCRIPT;
		}
	}

}
