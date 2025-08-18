package com.patbaumgartner.migroscouponbooster.properties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PropertiesValidationTest {

	@Autowired
	private Validator validator;

	@Test
	void shouldValidateMigrosUserProperties() {
		// Valid properties
		var validProps = new MigrosUserProperties("test@example.com", "password123");
		var violations = validator.validate(validProps);
		assertThat(violations).isEmpty();

		// Invalid email
		var invalidEmail = new MigrosUserProperties("invalid-email", "password123");
		violations = validator.validate(invalidEmail);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Email must be valid");

		// Empty password
		var emptyPassword = new MigrosUserProperties("test@example.com", "");
		violations = validator.validate(emptyPassword);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Password is required");
	}

	@Test
	void shouldValidatePlaywrightProperties() {
		// Valid properties
		var validProps = new PlaywrightProperties("https://login.migros.ch/", "https://login.migros.ch/login/password",
				50, 100, true, List.of("--no-sandbox"), 5000);
		var violations = validator.validate(validProps);
		assertThat(violations).isEmpty();

		// Invalid timeout
		var invalidTimeout = new PlaywrightProperties("https://login.migros.ch/",
				"https://login.migros.ch/login/password", 50, 100, true, List.of("--no-sandbox"), 500 // Too
																										// small
		);
		violations = validator.validate(invalidTimeout);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Timeout must be at least 1000ms");
	}

	@Test
	void shouldValidateMigrosSelectorsProperties() {
		// Valid properties
		var validProps = new MigrosSelectorsProperties("#input-email", "#input-password", "#button-submit",
				"#link-login-option-PASSWORD", "button:has-text('Accept')");
		var violations = validator.validate(validProps);
		assertThat(violations).isEmpty();

		// Empty selector
		var emptySelector = new MigrosSelectorsProperties("", "#input-password", "#button-submit",
				"#link-login-option-PASSWORD", "button:has-text('Accept')");
		violations = validator.validate(emptySelector);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Email input selector is required");
	}

	@Test
	void shouldValidateCumulusProperties() {
		// Valid properties
		var validUrls = new CumulusProperties.Urls("https://account.migros.ch", "/api/coupons",
				"https://account.migros.ch/coupons", "/api/activate");
		var validApi = new CumulusProperties.Api(java.time.Duration.ofMillis(500), 3, java.time.Duration.ofSeconds(30));
		var validBrowser = new CumulusProperties.Browser("Mozilla/5.0", "en-US");
		var validProps = new CumulusProperties(validUrls, validApi, validBrowser);

		var violations = validator.validate(validProps);
		assertThat(violations).isEmpty();

		// Invalid base URL
		var invalidUrls = new CumulusProperties.Urls("not-a-url", "/api/coupons", "https://account.migros.ch/coupons",
				"/api/activate");
		var invalidProps = new CumulusProperties(invalidUrls, validApi, validBrowser);
		violations = validator.validate(invalidProps);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Base URL must be a valid URL");

		// Invalid max retries
		var invalidApi = new CumulusProperties.Api(java.time.Duration.ofMillis(500), 15,
				java.time.Duration.ofSeconds(30));
		var invalidApiProps = new CumulusProperties(validUrls, invalidApi, validBrowser);
		violations = validator.validate(invalidApiProps);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).contains("Max retries cannot exceed 10");
	}

}
