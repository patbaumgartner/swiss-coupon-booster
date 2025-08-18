package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Playwright browser automation settings. Controls how the
 * browser behaves during the Coop authentication process.
 *
 * @param loginUrl the URL of the Coop login page to navigate to initially
 * @param datadomeCookieValue optional DataDome cookie value for bypassing bot detection
 * @param typingDelayMs delay in milliseconds between individual keystrokes to simulate
 * human typing
 * @param slowMoMs general slowdown in milliseconds for all Playwright actions to appear
 * more human-like
 * @param headless whether to run the browser in headless mode (true) or with visible UI
 * (false)
 * @param timeoutMs maximum time in milliseconds to wait for page elements and navigation
 * actions
 */
@ConfigurationProperties(prefix = "coop.playwright")
@Validated
public record CoopPlaywrightProperties(

		@NotBlank(message = "Login URL is required") @URL(message = "Login URL must be a valid URL") String loginUrl,

		@NotBlank(message = "DataDome cookie value is required for bot protection bypass") String datadomeCookieValue,

		@Min(value = 0, message = "Typing delay cannot be negative") @Max(value = 1000,
				message = "Typing delay cannot exceed 1000ms") int typingDelayMs,

		@Min(value = 0, message = "Slow motion delay cannot be negative") @Max(value = 5000,
				message = "Slow motion delay cannot exceed 5000ms") int slowMoMs,

		boolean headless,

		@Min(value = 1000, message = "Timeout must be at least 1000ms") @Max(value = 300000,
				message = "Timeout cannot exceed 300000ms (5 minutes)") int timeoutMs) {

}
