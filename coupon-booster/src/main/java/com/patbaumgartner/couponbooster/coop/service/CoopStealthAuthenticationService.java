package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.CoopPatchrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.service.AbstractStealthAuthenticationService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * {@link AuthenticationService} implementation that delegates Coop login to the
 * Patchright stealth sidecar service ({@code patchright}).
 * <p>
 * The sidecar runs a hardened Chromium (Patchright) instance that bypasses DataDome bot
 * detection. This service calls {@code POST /login/coop} on the sidecar, receives session
 * cookies, and wraps them in an
 * {@link com.patbaumgartner.couponbooster.model.AuthenticationResult}.
 * <p>
 * Active when {@code coop.auth.mode=sidecar} (set via {@code COOP_AUTH_MODE=sidecar}).
 * The default when no env var is set is {@code browser}, which is suitable for local
 * development without Docker.
 *
 * @see CoopAuthenticationService
 * @see CoopPatchrightProperties
 */
@Service
@Qualifier("coopAuth")
@ConditionalOnProperty(name = "coop.auth.mode", havingValue = "sidecar")
public class CoopStealthAuthenticationService extends AbstractStealthAuthenticationService {

	/**
	 * Constructs a new {@code CoopStealthAuthenticationService}.
	 * @param userCredentials Coop account credentials
	 * @param patchrightProperties configuration for the sidecar endpoint
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	public CoopStealthAuthenticationService(CoopUserProperties userCredentials,
			CoopPatchrightProperties patchrightProperties, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		super(Objects.requireNonNull(userCredentials, "User credentials cannot be null")::email,
				userCredentials::password,
				"Coop credentials missing. Configure COOP_USER_EMAIL and COOP_USER_PASSWORD.", "/login/coop",
				Objects.requireNonNull(patchrightProperties, "Patchright properties cannot be null").url(),
				restClientBuilder, objectMapper);
	}

}
