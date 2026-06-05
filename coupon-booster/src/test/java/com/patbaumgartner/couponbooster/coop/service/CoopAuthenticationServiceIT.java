package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration-test")
@Disabled("This test requires real user credentials and is expected to fail with dummy credentials.")
class CoopAuthenticationServiceIT {

	@Autowired
	private CoopAuthenticationService coopAuthenticationService;

	@Test
	void performAuthentication_shouldFailWithDummyCredentials() {
		// When
		AuthenticationResult result = coopAuthenticationService.performAuthentication();

		// Then
		assertThat(result).isNotNull();
		assertThat(result.isSuccessful()).isFalse();
	}

}
