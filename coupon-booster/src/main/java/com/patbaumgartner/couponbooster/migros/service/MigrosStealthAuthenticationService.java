package com.patbaumgartner.couponbooster.migros.service;

import com.patbaumgartner.couponbooster.migros.properties.MigrosStealthServiceProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.service.AbstractStealthAuthenticationService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * {@link AuthenticationService} implementation that delegates Migros login to the
 * Patchright stealth sidecar service ({@code stealth-service}).
 * <p>
 * Calls {@code POST /login/migros} on the sidecar, receives session cookies, and wraps
 * them in an {@link com.patbaumgartner.couponbooster.model.AuthenticationResult}.
 * <p>
 * Active when {@code migros.auth.mode=sidecar} (set via
 * {@code MIGROS_AUTH_MODE=sidecar}). The default when no env var is set is
 * {@code browser}, which is suitable for local development without Docker.
 *
 * @see MigrosAuthenticationService
 * @see MigrosStealthServiceProperties
 */
@Service
@Qualifier("migrosAuth")
@ConditionalOnProperty(name = "migros.auth.mode", havingValue = "sidecar")
public class MigrosStealthAuthenticationService extends AbstractStealthAuthenticationService {

	/**
	 * Constructs a new {@code MigrosStealthAuthenticationService}.
	 * @param userCredentials Migros account credentials
	 * @param stealthServiceProperties configuration for the sidecar endpoint
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	public MigrosStealthAuthenticationService(MigrosUserProperties userCredentials,
			MigrosStealthServiceProperties stealthServiceProperties, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		super(Objects.requireNonNull(userCredentials, "User credentials cannot be null")::email,
				userCredentials::password,
				"Migros credentials missing. Configure MIGROS_USER_EMAIL and MIGROS_USER_PASSWORD.", "/login/migros",
				Objects.requireNonNull(stealthServiceProperties, "Stealth service properties cannot be null").url(),
				restClientBuilder, objectMapper);
	}

}
