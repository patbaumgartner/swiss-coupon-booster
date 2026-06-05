package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.migros.properties.MigrosStealthServiceProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link AuthenticationService} implementation that delegates Migros login to the
 * Patchright stealth sidecar service ({@code stealth-service}).
 * <p>
 * Calls {@code POST /login/migros} on the sidecar, receives session cookies, and wraps
 * them in an {@link AuthenticationResult}.
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
public class MigrosStealthAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(MigrosStealthAuthenticationService.class);

	private final MigrosUserProperties userCredentials;

	private final MigrosStealthServiceProperties stealthServiceProperties;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

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
		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.stealthServiceProperties = Objects.requireNonNull(stealthServiceProperties,
				"Stealth service properties cannot be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
		this.restClient = restClientBuilder.baseUrl(stealthServiceProperties.url()).build();
	}

	/**
	 * Calls the stealth sidecar {@code POST /login/migros} endpoint and maps the response
	 * to an {@link AuthenticationResult}.
	 * @return a successful result containing session cookies, or a failed result if the
	 * sidecar returns an error
	 */
	@Override
	public AuthenticationResult performAuthentication() {
		var startTime = System.currentTimeMillis();

		if (userCredentials.email() == null || userCredentials.email().isBlank() || userCredentials.password() == null
				|| userCredentials.password().isBlank()) {
			var duration = System.currentTimeMillis() - startTime;
			return AuthenticationResult
				.failed("Migros credentials missing. Configure MIGROS_USER_EMAIL and MIGROS_USER_PASSWORD.", duration);
		}

		log.info("Requesting Migros stealth login from sidecar at {}", stealthServiceProperties.url());

		try {
			var requestBody = objectMapper.createObjectNode()
				.put("email", userCredentials.email())
				.put("password", userCredentials.password())
				.toString();

			var responseEntity = restClient.post()
				.uri("/login/migros")
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.toEntity(String.class);

			if (responseEntity.getStatusCode() != HttpStatus.OK || responseEntity.getBody() == null) {
				var duration = System.currentTimeMillis() - startTime;
				var msg = "Sidecar returned unexpected status: " + responseEntity.getStatusCode();
				log.error(msg);
				return AuthenticationResult.failed(msg, duration);
			}

			return parseSidecarResponse(responseEntity.getBody(), startTime);
		}
		catch (RestClientException ex) {
			var duration = System.currentTimeMillis() - startTime;
			log.error("Sidecar HTTP request failed: {}", ex.getMessage(), ex);
			return AuthenticationResult.failed("Sidecar request failed: " + ex.getMessage(), duration);
		}
		catch (Exception ex) {
			var duration = System.currentTimeMillis() - startTime;
			log.error("Unexpected error calling stealth sidecar: {}", ex.getMessage(), ex);
			return AuthenticationResult.failed("Unexpected sidecar error: " + ex.getMessage(), duration);
		}
	}

	private AuthenticationResult parseSidecarResponse(String body, long startTime) {
		try {
			JsonNode root = objectMapper.readTree(body);
			String userAgent = root.path("userAgent").asString(null);
			String language = root.path("language").asString(null);

			List<Cookie> cookies = new ArrayList<>();
			JsonNode cookiesNode = root.path("cookies");
			if (cookiesNode.isArray()) {
				for (JsonNode c : cookiesNode) {
					var cookie = new Cookie(c.path("name").asString(""), c.path("value").asString(""));
					cookie.domain = c.path("domain").asString(null);
					cookie.path = c.path("path").asString("/");
					cookie.httpOnly = c.path("httpOnly").asBoolean(false);
					cookie.secure = c.path("secure").asBoolean(false);
					double exp = c.path("expires").asDouble(-1);
					if (exp > 0) {
						cookie.expires = exp;
					}
					cookies.add(cookie);
				}
			}

			var duration = System.currentTimeMillis() - startTime;
			log.info("Migros stealth login successful — {} cookies in {}ms", cookies.size(), duration);
			return AuthenticationResult.successful(cookies, duration, userAgent, language);
		}
		catch (Exception ex) {
			var duration = System.currentTimeMillis() - startTime;
			log.error("Failed to parse sidecar response: {}", ex.getMessage(), ex);
			return AuthenticationResult.failed("Failed to parse sidecar response: " + ex.getMessage(), duration);
		}
	}

}
