package com.patbaumgartner.couponbooster.migros.service;

import com.patbaumgartner.couponbooster.migros.properties.MigrosPatchrightProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.service.AbstractSidecarAuthenticationService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * {@link AuthenticationService} implementation that delegates Migros login to the
 * Patchright sidecar service ({@code patchright}).
 * <p>
 * Calls {@code POST /login/migros} on the sidecar, receives session cookies, and wraps
 * them in an {@link com.patbaumgartner.couponbooster.model.AuthenticationResult}.
 *
 * @see MigrosPatchrightProperties
 */
@Service
@Qualifier("migrosAuth")
public class MigrosSidecarAuthenticationService extends AbstractSidecarAuthenticationService {

	/**
	 * Constructs a new {@code MigrosSidecarAuthenticationService}.
	 * @param userCredentials Migros account credentials
	 * @param patchrightProperties configuration for the sidecar endpoint
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	public MigrosSidecarAuthenticationService(MigrosUserProperties userCredentials,
			MigrosPatchrightProperties patchrightProperties, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		super(Objects.requireNonNull(userCredentials, "User credentials cannot be null")::email,
				userCredentials::password,
				"Migros credentials missing. Configure MIGROS_USER_EMAIL and MIGROS_USER_PASSWORD.", "/login/migros",
				Objects.requireNonNull(patchrightProperties, "Patchright properties cannot be null").url(),
				restClientBuilder, objectMapper);
	}

}
