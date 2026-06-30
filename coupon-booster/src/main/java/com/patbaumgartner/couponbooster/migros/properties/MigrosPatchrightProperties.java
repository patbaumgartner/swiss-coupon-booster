package com.patbaumgartner.couponbooster.migros.properties;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Patchright stealth login sidecar (Migros).
 *
 * @param url base URL of the Patchright sidecar (e.g. {@code http://patchright:8000})
 */
@ConfigurationProperties(prefix = "migros.patchright")
@Validated
public record MigrosPatchrightProperties(

		@NotBlank(message = "Patchright sidecar URL is required") @URL(
				message = "Patchright sidecar URL must be a valid URL") String url) {
}
