package com.patbaumgartner.migroscouponbooster.properties;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Cumulus coupon service integration. Contains all necessary
 * settings for API endpoints, timing, and browser simulation.
 *
 * @param urls URL configuration for various Cumulus service endpoints
 * @param api API timing and retry configuration settings
 * @param browser browser simulation configuration for HTTP requests
 */
@ConfigurationProperties(prefix = "cumulus")
@Validated
public record CumulusProperties(@Valid @NotNull Urls urls, @Valid @NotNull Api api, @Valid @NotNull Browser browser) {

	/**
	 * URL configuration for Cumulus service endpoints.
	 *
	 * @param baseUrl the base URL for the Cumulus service (e.g.,
	 * https://account.migros.ch)
	 * @param couponsEndpoint the API endpoint to fetch available coupons
	 * @param couponsReferer the referer header value required for coupon API requests
	 * @param activationEndpoint the API endpoint to activate individual coupons
	 */
	public record Urls(
			@NotBlank(message = "Base URL is required") @URL(message = "Base URL must be a valid URL") String baseUrl,

			@NotBlank(message = "Coupons endpoint is required") String couponsEndpoint,

			@NotBlank(message = "Coupons referer is required") @URL(
					message = "Coupons referer must be a valid URL") String couponsReferer,

			@NotBlank(message = "Activation endpoint is required") String activationEndpoint) {

	}

	/**
	 * API timing and retry configuration.
	 *
	 * @param requestDelay delay between consecutive API requests to avoid rate limiting
	 * @param maxRetries maximum number of retry attempts for failed API requests
	 * @param timeout maximum time to wait for API request completion
	 */
	public record Api(@NotNull(message = "Request delay is required") Duration requestDelay,

			@Min(value = 0, message = "Max retries cannot be negative") @Max(value = 10,
					message = "Max retries cannot exceed 10") int maxRetries,

			@NotNull(message = "Timeout is required") Duration timeout) {

	}

	/**
	 * Browser simulation configuration for HTTP requests.
	 *
	 * @param userAgent the User-Agent header value to simulate a real browser
	 * @param language the Accept-Language header value for localization preferences
	 */
	public record Browser(@NotBlank(message = "User agent is required") String userAgent,

			@NotBlank(message = "Language is required") String language) {

	}

}
