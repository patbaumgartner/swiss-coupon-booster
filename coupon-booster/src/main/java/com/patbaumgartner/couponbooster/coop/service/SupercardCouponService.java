package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.model.CouponDetail;
import com.patbaumgartner.couponbooster.service.AbstractCouponService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.CookieNames.AUTHENTICATION_DOMAIN;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.HttpHeaders.X_CLIENT_ID;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.HttpHeaders.X_CLIENT_ID_VALUE;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Service for managing Coop Supercard digital coupons (bons) through the Coop API.
 * <p>
 * This service handles the entire lifecycle of coupon management, including:
 * <ul>
 * <li>Extracting a JWT token using browser session cookies.</li>
 * <li>Fetching all available digital coupons.</li>
 * <li>Deactivating currently active coupons to free up slots.</li>
 * <li>Activating new, eligible coupons based on a predefined filter.</li>
 * </ul>
 * It interacts directly with the Coop Supercard web API using a {@link RestClient}.
 *
 * @see CouponService
 * @see SupercardProperties
 */
@Service
public class SupercardCouponService extends AbstractCouponService {

	private static final Logger log = LoggerFactory.getLogger(SupercardCouponService.class);

	private final SupercardProperties supercardProperties;

	private final ObjectMapper objectMapper;

	private final RestClient apiClient;

	/**
	 * Creates a new SuperCard coupon service.
	 * @param restClientBuilder Builder for creating the {@link RestClient} instance.
	 * @param objectMapper Jackson object mapper for JSON serialization/deserialization.
	 * @param supercardProperties Configuration properties for SuperCard API endpoints and
	 * browser settings.
	 */
	public SupercardCouponService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			SupercardProperties supercardProperties) {
		this.apiClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
		this.supercardProperties = supercardProperties;
	}

	/**
	 * Activates all available Supercard digital coupons.
	 * <p>
	 * This method orchestrates the entire coupon activation process:
	 * <ol>
	 * <li>Extracts a JWT token using the provided session cookies.</li>
	 * <li>Fetches all available digital coupons.</li>
	 * <li>Deactivates all currently active coupons to make space for new ones.</li>
	 * <li>Fetches the updated list of coupons.</li>
	 * <li>Filters for eligible, inactive coupons.</li>
	 * <li>Activates the filtered coupons.</li>
	 * <li>Fetches the final list of coupons to confirm activation.</li>
	 * </ol>
	 * @param sessionCookies authentication cookies from browser session
	 * @param userAgent the user agent of the browser
	 * @param language the language of the browser
	 * @return result containing activation statistics and coupon details
	 */
	@Override
	public CouponActivationResult activateAllAvailableCoupons(List<Cookie> sessionCookies, String userAgent,
			String language) {

		if (sessionCookies == null || sessionCookies.isEmpty()) {
			log.warn("No session cookies provided for coupon activation");
			return new CouponActivationResult(0, 0, List.of());
		}

		if (log.isInfoEnabled()) {
			log.info("Starting coupon activation process with {} session cookies", sessionCookies.size());
		}

		try {
			var filteredCookies = filterDomainSpecificCookies(sessionCookies, AUTHENTICATION_DOMAIN);
			if (filteredCookies.isEmpty()) {
				log.warn("No domain-specific cookies found for authentication domain: {}", AUTHENTICATION_DOMAIN);
				return new CouponActivationResult(0, 0, List.of());
			}

			String webapiBearerToken = extractJwtToken(filteredCookies, userAgent, language);
			var digitalCoupons = fetchDigitalCoupons(webapiBearerToken, userAgent, language);

			// Deactivate all ACTIVE digital coupons (best-effort; failure is logged but
			// does not abort)
			List<DigitalCoupon> activeCoupons = digitalCoupons.stream()
				.filter(item -> "ACTIVE".equals(item.status()))
				.toList();
			deactivateDigitalCoupons(activeCoupons, webapiBearerToken, userAgent, language);

			// Fetch updated list after deactivation
			digitalCoupons = fetchDigitalCoupons(webapiBearerToken, userAgent, language);
			List<DigitalCoupon> inactiveCoupons = Stream
				.concat(digitalCoupons.stream().filter(item -> item.textDiscountAmount().contains("5 Rappen")),
						digitalCoupons.stream()
							.filter(item -> "OPEN".equals(item.status()))
							.filter(this::filterProductTypes)
							.filter(item -> "retail".equals(item.shop())))
				.distinct()
				.limit(20)
				.toList();

			int intended = inactiveCoupons.size();
			log.info("Attempting to activate {} eligible coupons", intended);

			activateDigitalCoupons(inactiveCoupons, webapiBearerToken, userAgent, language);

			// Verify: fetch final state and compare against intended activations
			digitalCoupons = fetchDigitalCoupons(webapiBearerToken, userAgent, language);
			var intendedCodes = inactiveCoupons.stream().map(DigitalCoupon::code).toList();
			List<CouponDetail> activationResults = digitalCoupons.stream()
				.filter(item -> "ACTIVE".equals(item.status()) && intendedCodes.contains(item.code()))
				.map(item -> new CouponDetail(item.textDescription(), item.code(), true, item.textDiscountAmount()))
				.toList();

			int successfulActivations = activationResults.size();
			int failedActivations = intended - successfulActivations;

			if (failedActivations > 0) {
				log.warn("{} coupon(s) could not be confirmed as ACTIVE after activation attempt", failedActivations);
			}
			logActivationSummary(successfulActivations, failedActivations, intended);

			return new CouponActivationResult(successfulActivations, failedActivations, activationResults);
		}
		catch (Exception exception) {
			log.error("Coupon activation process failed unexpectedly: {}", exception.getMessage(), exception);
			return new CouponActivationResult(0, 1, List
				.of(new CouponDetail("System Error", "unknown", false, "Process failed: " + exception.getMessage())));
		}

	}

	/**
	 * Extracts the JWT (JSON Web Token) from the Supercard configuration endpoint.
	 * <p>
	 * This token is required for all subsequent API calls to manage coupons.
	 * @param sessionCookies The list of browser cookies from an authenticated session.
	 * @param userAgent the browser user-agent string
	 * @param language the browser language string
	 * @return The extracted JWT token as a String.
	 * @throws CouponBoosterException if the API call fails, returns HTML (DataDome
	 * challenge still active), or the token is missing.
	 */
	public String extractJwtToken(List<Cookie> sessionCookies, String userAgent, String language) {
		String cookieHeader = buildCookieHeader(sessionCookies);

		ResponseEntity<String> configResponse = apiClient.get()
			.uri(supercardProperties.urls().configUrl())
			.accept(APPLICATION_JSON)
			.header(USER_AGENT, userAgent)
			.header(ACCEPT_LANGUAGE, language)
			.header(COOKIE, cookieHeader)
			.header(REFERER, supercardProperties.urls().configUrlReferer())
			.retrieve()
			.toEntity(String.class);

		MediaType contentType = configResponse.getHeaders().getContentType();
		if (configResponse.getStatusCode() != HttpStatus.OK) {
			throw new CouponBoosterException(
					"JWT extraction failed: config endpoint returned HTTP " + configResponse.getStatusCode()
							+ ". The login session may have expired or the sidecar login did not complete.");
		}
		if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
			throw new CouponBoosterException("JWT extraction failed: config endpoint returned HTML instead of JSON. "
					+ "This usually means the session is not authenticated or DataDome is still active. "
					+ "Check stealth sidecar logs and screenshots in /data/screenshots.");
		}

		JsonNode rootNode = objectMapper.readTree(configResponse.getBody());
		String token = rootNode.path("jwtToken").asString(null);
		if (token == null || token.isBlank()) {
			throw new CouponBoosterException(
					"JWT extraction failed: 'jwtToken' field is missing or empty in config response. "
							+ "Response body: " + configResponse.getBody());
		}
		log.debug("JWT token extracted successfully");
		return token;
	}

	/**
	 * Fetches the complete list of digital coupons from the Supercard API.
	 */
	private List<DigitalCoupon> fetchDigitalCoupons(String webapiBearerToken, String userAgent, String language) {

		ResponseEntity<String> collectionResponse = apiClient.get()
			.uri(supercardProperties.urls().couponsUrl())
			.header(HttpHeaders.USER_AGENT, userAgent)
			.header(HttpHeaders.ACCEPT_LANGUAGE, language)
			.accept(APPLICATION_JSON)
			.header(AUTHORIZATION, "Bearer " + webapiBearerToken)
			.header(X_CLIENT_ID, X_CLIENT_ID_VALUE)
			.retrieve()
			.toEntity(String.class);

		if (collectionResponse.getStatusCode() != HttpStatus.OK) {
			throw new CouponBoosterException("Digital bons retrieval failed.");
		}
		MediaType contentType = collectionResponse.getHeaders().getContentType();
		if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
			throw new CouponBoosterException("Digital bons retrieval failed: received HTML instead of JSON. "
					+ "Session may have expired or DataDome is still active.");
		}

		List<DigitalCoupon> digitalCouponCollection = new ArrayList<>();

		JsonNode rootNode = objectMapper.readTree(collectionResponse.getBody());

		JsonNode digitalCoupons = rootNode.path("dc");

		if (digitalCoupons.isArray()) {
			for (JsonNode coupon : digitalCoupons) {
				String code = coupon.path("code").asString();
				String status = coupon.path("status").asString();
				String shop = coupon.path("formatIdMain").asString();
				String textDescription = coupon.path("textDescription").asString();
				String textDiscountAmount = coupon.path("textDiscountAmount").asString();

				boolean isNew = coupon.path("isNew").asBoolean();
				boolean isRecommendation = coupon.path("isRecommendation").asBoolean();
				boolean hasLogoProduct = !"none".equals(coupon.path("logoProduct").asString("none"));

				List<String> productTypes = new ArrayList<>();
				JsonNode productTypeArray = coupon.path("productTypes");
				if (productTypeArray.isArray()) {
					for (JsonNode productType : productTypeArray) {
						productTypes.add(productType.asString());
					}
				}

				LocalDateTime endDate = LocalDateTime.parse(coupon.path("endDate").asString(),
						DateTimeFormatter.ISO_DATE_TIME);

				digitalCouponCollection.add(new DigitalCoupon(code, status, shop, isNew, isRecommendation,
						hasLogoProduct, productTypes, endDate, textDescription, textDiscountAmount));
				log.debug("Found digital coupon: {} - {}", code, status);
			}
		}

		return digitalCouponCollection;
	}

	// Package-private to allow direct testing without exposing to the public API.
	boolean filterProductTypes(DigitalCoupon coupon) {
		List<String> includeList = supercardProperties.couponFilter().includeProductTypes();

		for (String productType : coupon.productTypes()) {
			if (!includeList.contains(productType)) {
				return false;
			}
		}
		return true;
	}

	private void deactivateDigitalCoupons(List<DigitalCoupon> activeCoupons, String webapiBearerToken, String userAgent,
			String language) {

		List<String> couponCodes = activeCoupons.stream().map(DigitalCoupon::code).toList();

		if (couponCodes.isEmpty()) {
			log.info("No active coupons to deactivate.");
			return;
		}

		log.info("Deactivating {} active coupon(s)", couponCodes.size());

		ResponseEntity<String> deactivationResponse = apiClient.put()
			.uri(supercardProperties.urls().couponsDeactivationUrl())
			.header(HttpHeaders.USER_AGENT, userAgent)
			.header(HttpHeaders.ACCEPT_LANGUAGE, language)
			.accept(APPLICATION_JSON)
			.header(AUTHORIZATION, "Bearer " + webapiBearerToken)
			.header(X_CLIENT_ID, X_CLIENT_ID_VALUE)
			.contentType(APPLICATION_JSON)
			.body(objectMapper.writeValueAsString(new DigitalCouponCollection(couponCodes)))
			.retrieve()
			.toEntity(String.class);

		if (deactivationResponse.getStatusCode() != HttpStatus.OK) {
			log.error("Deactivation returned HTTP {} with body: {}", deactivationResponse.getStatusCode(),
					deactivationResponse.getBody());
			throw new CouponBoosterException(
					"Digital coupon deactivation failed with status: " + deactivationResponse.getStatusCode());
		}
		MediaType contentType = deactivationResponse.getHeaders().getContentType();
		if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
			log.error("Deactivation returned HTML (possible DataDome/session expiry). Body: {}",
					deactivationResponse.getBody());
			throw new CouponBoosterException("Digital coupon deactivation returned HTML – session may have expired.");
		}
		log.debug("Deactivation succeeded");
	}

	private void activateDigitalCoupons(List<DigitalCoupon> inactiveCoupons, String webapiBearerToken, String userAgent,
			String language) {

		List<String> couponCodes = inactiveCoupons.stream().map(DigitalCoupon::code).toList();

		if (couponCodes.isEmpty()) {
			log.info("No eligible coupons to activate.");
			return;
		}

		log.info("Activating {} coupon(s)", couponCodes.size());

		ResponseEntity<String> activationResponse = apiClient.put()
			.uri(supercardProperties.urls().couponsActivationUrl())
			.header(HttpHeaders.USER_AGENT, userAgent)
			.header(HttpHeaders.ACCEPT_LANGUAGE, language)
			.accept(APPLICATION_JSON)
			.header(AUTHORIZATION, "Bearer " + webapiBearerToken)
			.header(X_CLIENT_ID, X_CLIENT_ID_VALUE)
			.contentType(APPLICATION_JSON)
			.body(objectMapper.writeValueAsString(new DigitalCouponCollection(couponCodes)))
			.retrieve()
			.toEntity(String.class);

		if (activationResponse.getStatusCode() != HttpStatus.OK) {
			log.error("Activation returned HTTP {} with body: {}", activationResponse.getStatusCode(),
					activationResponse.getBody());
			throw new CouponBoosterException(
					"Digital coupon activation failed with status: " + activationResponse.getStatusCode());
		}
		MediaType contentType = activationResponse.getHeaders().getContentType();
		if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
			log.error("Activation returned HTML (possible DataDome/session expiry). Body: {}",
					activationResponse.getBody());
			throw new CouponBoosterException("Digital coupon activation returned HTML – session may have expired.");
		}
		log.debug("Activation request succeeded");
	}

	/**
	 * Represents the payload for activating or deactivating a collection of digital
	 * coupons.
	 */
	private record DigitalCouponCollection(List<String> codes) {
	}

	/**
	 * Represents a single digital coupon (bon) from the Supercard API.
	 */
	private record DigitalCoupon(String code, String status, String shop, boolean isNew, boolean isRecommendation,
			boolean hasLogoProduct, List<String> productTypes, LocalDateTime endDate, String textDescription,
			String textDiscountAmount) {
	}

}
