package com.patbaumgartner.couponbooster.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * Utility component for disabling WebAuthn functionality in browser contexts.
 * <p>
 * This is necessary to prevent WebAuthn (FIDO2) prompts from interfering with automated
 * login flows that rely on password-based authentication. The disabler works by loading a
 * JavaScript snippet that removes the `navigator.credentials` and `PublicKeyCredential`
 * objects from the `window` scope.
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
	 * Returns the JavaScript code needed to disable WebAuthn functionality.
	 * <p>
	 * It first attempts to load the script from the classpath resource
	 * {@code webauthn-disable.js}. If the resource cannot be loaded, it falls back to a
	 * hardcoded script.
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
