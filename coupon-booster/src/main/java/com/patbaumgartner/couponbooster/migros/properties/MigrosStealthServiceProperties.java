package com.patbaumgartner.couponbooster.migros.properties;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Patchright stealth login sidecar (Migros).
 *
 * @param url base URL of the stealth sidecar (e.g. {@code http://coop-stealth:8000})
 */
@ConfigurationProperties(prefix = "migros.stealth-service")
@Validated
public record MigrosStealthServiceProperties(

		@NotBlank(message = "Stealth service URL is required") @URL(
				message = "Stealth service URL must be a valid URL") String url) {
}
