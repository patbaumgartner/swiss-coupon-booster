package com.patbaumgartner.couponbooster.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebAuthnDisablerTest {

	@InjectMocks
	private WebAuthnDisabler webAuthnDisabler;

	@Test
	void getDisableScript_ShouldReturnNonEmptyScript() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).isNotNull();
		assertThat(script).isNotEmpty();
		assertThat(script).isNotBlank();
	}

	@Test
	void getDisableScript_ShouldContainWebAuthnDisablingCode() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).contains("navigator");
		assertThat(script).contains("credentials");
		assertThat(script).contains("PublicKeyCredential");
	}

	@Test
	void getDisableScript_ShouldContainObjectDefineProperty() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).contains("Object.defineProperty");
		assertThat(script).contains("undefined");
	}

	@Test
	void getDisableScript_ShouldHandleWindowNavigatorCheck() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).contains("window.navigator");
		assertThat(script).contains("if");
	}

	@Test
	void getDisableScript_ShouldHandlePublicKeyCredentialCheck() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script).contains("window.PublicKeyCredential");
	}

	@Test
	void getDisableScript_CalledMultipleTimes_ShouldReturnConsistentResult() {
		// When
		var script1 = webAuthnDisabler.getDisableScript();
		var script2 = webAuthnDisabler.getDisableScript();

		// Then
		assertThat(script1).isEqualTo(script2);
	}

	@Test
	void getDisableScript_ShouldReturnValidJavaScript() {
		// When
		var script = webAuthnDisabler.getDisableScript();

		// Then
		// Basic JavaScript syntax checks
		assertThat(script).doesNotContain("null");
		assertThat(script).contains("{");
		assertThat(script).contains("}");
		// Should not contain any obvious syntax errors
		assertThat(script).doesNotContain("undefined undefined");
	}

}
