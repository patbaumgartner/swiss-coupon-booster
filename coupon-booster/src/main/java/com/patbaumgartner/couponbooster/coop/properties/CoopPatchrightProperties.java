package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Patchright stealth login sidecar.
 *
 * @param url base URL of the Patchright sidecar (e.g.
 * {@code http://coupon-booster-patchright:8000})
 */
@ConfigurationProperties(prefix = "coop.patchright")
@Validated
public record CoopPatchrightProperties(

		@NotBlank(message = "Patchright sidecar URL is required") @URL(
				message = "Patchright sidecar URL must be a valid URL") String url) {
}
