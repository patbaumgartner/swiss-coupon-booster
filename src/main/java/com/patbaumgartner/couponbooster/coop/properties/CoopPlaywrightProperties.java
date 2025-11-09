package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for Playwright browser automation settings. Controls how the
 * browser behaves during the Coop authentication process.
 *
 * @param loginUrl the URL of the Coop login page to navigate to initially
 * @param datadomeCookieValue optional DataDome cookie value for bypassing bot detection
 * (no longer required with stealth measures)
 * @param typingDelayMs delay in milliseconds between individual keystrokes to simulate
 * human typing
 * @param slowMoMs general slowdown in milliseconds for all Playwright actions to appear
 * more human-like
 * @param headless whether to run the browser in headless mode (true) or with visible UI
 * (false)
 * @param chromeArgs list of Chrome browser arguments to customize browser behavior and
 * security settings
 * @param timeoutMs maximum time in milliseconds to wait for page elements and navigation
 * actions
 */
@ConfigurationProperties(prefix = "coop.playwright")
@Validated
public record CoopPlaywrightProperties(

		@NotBlank(message = "Login URL is required") @URL(message = "Login URL must be a valid URL") String loginUrl,

		// Changed: DataDome cookie is now optional - stealth measures should suffice
		String datadomeCookieValue,

		@Min(value = 0, message = "Typing delay cannot be negative") @Max(value = 1000,
				message = "Typing delay cannot exceed 1000ms") int typingDelayMs,

		@Min(value = 0, message = "Slow motion delay cannot be negative") @Max(value = 5000,
				message = "Slow motion delay cannot exceed 5000ms") int slowMoMs,

		boolean headless,

		@NotNull(message = "Chrome arguments list is required") List<String> chromeArgs,

		@Min(value = 1000, message = "Timeout must be at least 1000ms") @Max(value = 300000,
				message = "Timeout cannot exceed 300000ms (5 minutes)") int timeoutMs) {

	public CoopPlaywrightProperties(String loginUrl, String datadomeCookieValue, int typingDelayMs, int slowMoMs,
			boolean headless, List<String> chromeArgs, int timeoutMs) {
		this.loginUrl = loginUrl;
		// Allow empty/null cookie value - stealth measures should handle bot detection
		this.datadomeCookieValue = datadomeCookieValue == null || datadomeCookieValue.isBlank() ? ""
				: datadomeCookieValue;
		this.typingDelayMs = typingDelayMs;
		this.slowMoMs = slowMoMs;
		this.headless = headless;
		this.chromeArgs = chromeArgs == null ? List.of() : List.copyOf(chromeArgs);
		this.timeoutMs = timeoutMs;
	}

	public List<String> chromeArgs() {
		return chromeArgs; // Already immutable from constructor
	}
}
