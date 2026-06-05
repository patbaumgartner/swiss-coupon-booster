package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Coop user authentication credentials. These credentials
 * are used to authenticate with the Coop login system.
 *
 * @param email the user's email address for Coop account authentication
 * @param password the user's password for Coop account authentication
 */
@ConfigurationProperties(prefix = "coop.user")
@Validated
public record CoopUserProperties(

		@Email(message = "Email must be valid") @NotBlank(message = "Email is required") String email,

		@NotBlank(message = "Password is required") String password) {
}
