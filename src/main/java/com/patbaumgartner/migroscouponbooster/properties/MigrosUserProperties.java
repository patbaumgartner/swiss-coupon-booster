package com.patbaumgartner.migroscouponbooster.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Migros user authentication credentials. These credentials
 * are used to authenticate with the Migros login system.
 *
 * @param email the user's email address for Migros account authentication
 * @param password the user's password for Migros account authentication
 */
@ConfigurationProperties(prefix = "migros.user")
@Validated
public record MigrosUserProperties(

		@Email(message = "Email must be valid") @NotBlank(message = "Email is required") String email,

		@NotBlank(message = "Password is required") String password) {
}
