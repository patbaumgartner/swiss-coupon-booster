package com.patbaumgartner.couponbooster.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebAuthnDisablerTest {

	private final WebAuthnDisabler webAuthnDisabler = new WebAuthnDisabler();

	@Test
	void getDisableScript_shouldReturnScriptFromFile() {
		// Given
		// A file named "webauthn-disable.js" exists in src/test/resources

		// When
		String script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).isNotBlank();
		assertThat(script).contains("window.navigator.credentials");
	}

}
