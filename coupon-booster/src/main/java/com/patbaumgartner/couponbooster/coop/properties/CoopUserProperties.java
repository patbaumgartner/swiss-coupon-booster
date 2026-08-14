package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Coop user authentication credentials.
 * <p>
 * Both values are optional so the application still starts when only the other retailer
 * is configured. A missing credential is reported as a failed authentication with an
 * actionable message rather than as a context startup failure. The address is still
 * validated when present, so a typo is caught at startup instead of after a browser
 * round-trip.
 *
 * @param email the user's email address for Coop account authentication
 * @param password the user's password for Coop account authentication
 */
@ConfigurationProperties(prefix = "coop.user")
@Validated
public record CoopUserProperties(@Email(message = "Email must be valid") String email, String password) {
}
