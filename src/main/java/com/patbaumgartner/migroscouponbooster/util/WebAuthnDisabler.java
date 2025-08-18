package com.patbaumgartner.migroscouponbooster.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * Utility component for disabling WebAuthn functionality in browser contexts. This is
 * necessary to prevent WebAuthn prompts from interfering with automated login flows.
 */
@Component
public class WebAuthnDisabler {

	private static final Logger log = LoggerFactory.getLogger(WebAuthnDisabler.class);

	private static final String FALLBACK_SCRIPT = """
			// Disable WebAuthn by removing credentials API and PublicKeyCredential
			if (window.navigator && window.navigator.credentials) {
			    Object.defineProperty(window.navigator, 'credentials', {
			        value: undefined,
			        writable: false,
			        configurable: false
			    });
			}

			if (window.PublicKeyCredential) {
			    Object.defineProperty(window, 'PublicKeyCredential', {
			        value: undefined,
			        writable: false,
			        configurable: false
			    });
			}
			""";

	/**
	 * Returns the JavaScript code needed to disable WebAuthn functionality. First
	 * attempts to load from external file, falls back to embedded script if needed.
	 * @return JavaScript code as a string
	 */
	public String getDisableScript() {
		try {
			var scriptResource = new ClassPathResource("webauthn-disable.js");
			return StreamUtils.copyToString(scriptResource.getInputStream(), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			log.error("Could not load external WebAuthn disable script, using fallback: {}", exception.getMessage(),
					exception);
			return FALLBACK_SCRIPT;
		}
	}

}
