package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for CSS/DOM selectors used in web automation. These selectors
 * are used by Playwright to locate specific elements on Coop login pages.
 *
 * @param loginLink CSS selector for the login link on the main page
 * @param usernameInput CSS selector for the username input field on the login page
 * @param passwordInput CSS selector for the password input field on the login page
 * @param submitButton CSS selector for the submit/continue button used in login flow
 * @param cookieAcceptButton CSS selector for the cookie consent acceptance button
 */
@ConfigurationProperties(prefix = "coop.selectors")
@Validated
public record CoopSelectorsProperties(

		@NotBlank(message = "Login link selector is required") String loginLink,

		@NotBlank(message = "Username input selector is required") String usernameInput,

		@NotBlank(message = "Password input selector is required") String passwordInput,

		@NotBlank(message = "Submit button selector is required") String submitButton,

		@NotBlank(message = "Cookie accept button selector is required") String cookieAcceptButton) {
}
