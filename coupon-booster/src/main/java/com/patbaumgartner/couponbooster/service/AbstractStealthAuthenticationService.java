package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Abstract base class for stealth sidecar authentication services.
 * <p>
 * Encapsulates the shared logic for delegating login to the Patchright stealth sidecar
 * service: credential validation, HTTP request to the sidecar, cookie parsing, and error
 * handling. Subclasses supply the provider-specific login URI and the missing-credentials
 * message via constructor arguments.
 *
 * @see com.patbaumgartner.couponbooster.coop.service.CoopStealthAuthenticationService
 * @see com.patbaumgartner.couponbooster.migros.service.MigrosStealthAuthenticationService
 */
public abstract class AbstractStealthAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(AbstractStealthAuthenticationService.class);

	private final Supplier<String> emailSupplier;

	private final Supplier<String> passwordSupplier;

	private final String credentialsMissingMessage;

	private final String loginUri;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Constructs a new stealth authentication service.
	 * @param emailSupplier supplies the user e-mail address at authentication time
	 * @param passwordSupplier supplies the user password at authentication time
	 * @param credentialsMissingMessage error message returned when credentials are absent
	 * @param loginUri sidecar endpoint path (e.g. {@code /login/coop})
	 * @param sidecarUrl base URL of the stealth sidecar (used to build the REST client)
	 * @param restClientBuilder Spring REST client builder
	 * @param objectMapper Jackson object mapper
	 */
	protected AbstractStealthAuthenticationService(Supplier<String> emailSupplier, Supplier<String> passwordSupplier,
			String credentialsMissingMessage, String loginUri, String sidecarUrl, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper) {
		this.emailSupplier = Objects.requireNonNull(emailSupplier, "emailSupplier cannot be null");
		this.passwordSupplier = Objects.requireNonNull(passwordSupplier, "passwordSupplier cannot be null");
		this.credentialsMissingMessage = Objects.requireNonNull(credentialsMissingMessage,
				"credentialsMissingMessage cannot be null");
		this.loginUri = Objects.requireNonNull(loginUri, "loginUri cannot be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
		this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder cannot be null")
			.baseUrl(sidecarUrl)
			.build();
	}

	/**
	 * Calls the stealth sidecar login endpoint and maps the response to an
	 * {@link AuthenticationResult}.
	 * @return a successful result containing session cookies, or a failed result if
	 * credentials are missing or the sidecar returns an error
	 */
	@Override
	public AuthenticationResult performAuthentication() {
		var startTime = System.currentTimeMillis();
		String email = emailSupplier.get();
		String password = passwordSupplier.get();

		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			var duration = System.currentTimeMillis() - startTime;
			return AuthenticationResult.failed(credentialsMissingMessage, duration);
		}

		log.info("Requesting stealth login from sidecar via {}", loginUri);

		try {
			var requestBody = objectMapper.createObjectNode().put("email", email).put("password", password).toString();

			var responseEntity = restClient.post()
				.uri(loginUri)
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
					Cookie cookie = new Cookie(node.path("name").asString(""), node.path("value").asString(""));
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
