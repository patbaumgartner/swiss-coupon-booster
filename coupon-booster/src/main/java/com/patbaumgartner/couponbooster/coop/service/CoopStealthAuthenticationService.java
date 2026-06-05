package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.properties.CoopStealthServiceProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
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
 * {@link AuthenticationService} implementation that delegates Coop login to the
 * Patchright stealth sidecar service ({@code stealth-service}).
 * <p>
 * The sidecar runs a hardened Chromium (Patchright) instance that bypasses DataDome bot
 * detection. This service calls {@code POST /login/coop} on the sidecar, receives session
 * cookies, and wraps them in an {@link AuthenticationResult}.
 * <p>
 * Active when {@code coop.auth.mode=sidecar} (set via {@code COOP_AUTH_MODE=sidecar}).
 * The default when no env var is set is {@code browser}, which is suitable for local
 * development without Docker.
 *
 * @see CoopAuthenticationService
 * @see CoopStealthServiceProperties
 */
@Service
@Qualifier("coopAuth")
@ConditionalOnProperty(name = "coop.auth.mode", havingValue = "sidecar")
public class CoopStealthAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(CoopStealthAuthenticationService.class);

	private final CoopUserProperties userCredentials;

	private final CoopStealthServiceProperties stealthServiceProperties;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Constructs a new {@code CoopStealthAuthenticationService}.
	 * @param userCredentials Coop account credentials
	 * @param stealthServiceProperties configuration for the sidecar endpoint
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	public CoopStealthAuthenticationService(CoopUserProperties userCredentials,
			CoopStealthServiceProperties stealthServiceProperties, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.stealthServiceProperties = Objects.requireNonNull(stealthServiceProperties,
				"Stealth service properties cannot be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
		this.restClient = restClientBuilder.baseUrl(stealthServiceProperties.url()).build();
	}

	/**
	 * Calls the stealth sidecar {@code POST /login/coop} endpoint and maps the response
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
				.failed("Coop credentials missing. Configure COOP_USER_EMAIL and COOP_USER_PASSWORD.", duration);
		}

		log.info("Requesting stealth login from sidecar at {}", stealthServiceProperties.url());

		try {
			var requestBody = objectMapper.createObjectNode()
				.put("email", userCredentials.email())
				.put("password", userCredentials.password())
				.toString();

			var responseEntity = restClient.post()
				.uri("/login/coop")
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
			JsonNode cookieArray = root.path("cookies");
			if (cookieArray.isArray()) {
				for (JsonNode node : cookieArray) {
					Cookie cookie = new Cookie(node.path("name").asString(), node.path("value").asString());
					String domain = node.path("domain").asString(null);
					if (domain != null) {
						cookie.setDomain(domain);
					}
					String path = node.path("path").asString(null);
					if (path != null) {
						cookie.setPath(path);
					}
					double expires = node.path("expires").asDouble(-1);
					if (expires > 0) {
						cookie.setExpires(expires);
					}
					cookie.setHttpOnly(node.path("httpOnly").asBoolean(false));
					cookie.setSecure(node.path("secure").asBoolean(false));
					cookies.add(cookie);
				}
			}

			var duration = System.currentTimeMillis() - startTime;
			log.info("Sidecar login successful: {} cookies received in {}ms", cookies.size(), duration);
			return AuthenticationResult.successful(cookies, duration, userAgent, language);
		}
		catch (Exception ex) {
			var duration = System.currentTimeMillis() - startTime;
			log.error("Failed to parse sidecar response: {}", ex.getMessage(), ex);
			return AuthenticationResult.failed("Failed to parse sidecar response: " + ex.getMessage(), duration);
		}
	}

}
