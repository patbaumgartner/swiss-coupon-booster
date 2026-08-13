package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.CoopPatchrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.service.AbstractSidecarAuthenticationService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * {@link AuthenticationService} implementation that delegates Coop login to the
 * Patchright sidecar service ({@code patchright}).
 * <p>
 * The sidecar runs a hardened Chromium (Patchright) instance behind a virtual display,
 * which bypasses DataDome bot detection. This service calls {@code POST /login/coop} on
 * the sidecar, receives session cookies, and wraps them in an
 * {@link com.patbaumgartner.couponbooster.model.AuthenticationResult}.
 *
 * @see CoopPatchrightProperties
 */
@Service
@Qualifier("coopAuth")
public class CoopSidecarAuthenticationService extends AbstractSidecarAuthenticationService {

	/**
	 * Constructs a new {@code CoopSidecarAuthenticationService}.
	 * @param userCredentials Coop account credentials
	 * @param patchrightProperties configuration for the sidecar endpoint
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	public CoopSidecarAuthenticationService(CoopUserProperties userCredentials,
			CoopPatchrightProperties patchrightProperties, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		super(Objects.requireNonNull(userCredentials, "User credentials cannot be null")::email,
				userCredentials::password,
				"Coop credentials missing. Configure COOP_USER_EMAIL and COOP_USER_PASSWORD.", "/login/coop",
				Objects.requireNonNull(patchrightProperties, "Patchright properties cannot be null").url(),
				restClientBuilder, objectMapper);
	}

}
