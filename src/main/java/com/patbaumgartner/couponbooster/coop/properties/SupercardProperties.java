package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Coop SuperCard API integration including endpoint URLs and
 * browser simulation settings.
 */
@ConfigurationProperties(prefix = "supercard")
@Validated
public record SupercardProperties(@Valid @NotNull Urls urls) {

	/**
	 * API endpoint URLs for SuperCard coupon management operations.
	 *
	 * @param baseUrl base URL for the SuperCard API
	 * @param configUrl URL for retrieving PWA configuration and JWT token
	 * @param configUrlReferer referer header value for config requests
	 * @param couponsUrl URL for retrieving available digital coupons
	 * @param couponsActivationUrl URL for activating digital coupons
	 * @param couponsDeactivationUrl URL for deactivating digital coupons
	 */
	public record Urls(
			@NotBlank(message = "Base URL is required") @URL(message = "Base URL must be a valid URL") String baseUrl,

			@NotBlank(message = "Config URL is required") @URL(
					message = "Config URL must be a valid URL") String configUrl,

			@NotBlank(message = "Config URL referer is required") @URL(
					message = "Config URL referer must be a valid URL") String configUrlReferer,

			@NotBlank(message = "Coupons URL is required") @URL(
					message = "Coupons URL must be a valid URL") String couponsUrl,

			@NotBlank(message = "Coupons activation URL is required") @URL(
					message = "Coupons activation URL must be a valid URL") String couponsActivationUrl,

			@NotBlank(message = "Coupons deactivation URL is required") @URL(
					message = "Coupons deactivation URL must be a valid URL") String couponsDeactivationUrl

	) {
	}
}
