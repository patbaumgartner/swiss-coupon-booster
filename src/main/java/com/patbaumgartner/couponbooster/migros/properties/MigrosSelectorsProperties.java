package com.patbaumgartner.couponbooster.migros.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for CSS/DOM selectors used in web automation. These selectors
 * are used by Playwright to locate specific elements on Migros login pages.
 *
 * @param emailInput CSS selector for the email input field on the login page
 * @param passwordInput CSS selector for the password input field on the login page
 * @param submitButton CSS selector for the submit/continue button used in login flow
 * @param passwordLoginLink CSS selector for the link to switch to password login option
 * @param cookieAcceptButton CSS selector for the cookie consent acceptance button
 */
@ConfigurationProperties(prefix = "migros.selectors")
@Validated
public record MigrosSelectorsProperties(

		@NotBlank(message = "Email input selector is required") String emailInput,

		@NotBlank(message = "Password input selector is required") String passwordInput,

		@NotBlank(message = "Submit button selector is required") String submitButton,

		@NotBlank(message = "Password login link selector is required") String passwordLoginLink,

		@NotBlank(message = "Cookie accept button selector is required") String cookieAcceptButton) {
}
