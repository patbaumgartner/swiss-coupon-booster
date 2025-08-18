package com.patbaumgartner.couponbooster.coop.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.model.CouponDetail;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.CookieNames.AUTHENTICATION_DOMAIN;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.HttpHeaders.X_CLIENT_ID;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.HttpHeaders.X_CLIENT_ID_VALUE;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Service for managing Coop SuperCard digital coupons (bons) through the Coop API.
 * <p>
 * This service handles the entire lifecycle of coupon management, including:
 * <ul>
 * <li>Extracting a JWT token using browser session cookies.</li>
 * <li>Fetching all available digital coupons.</li>
 * <li>Deactivating currently active coupons to free up slots.</li>
 * <li>Activating new, eligible coupons based on a predefined filter.</li>
 * </ul>
 * It interacts directly with the Coop SuperCard web API.
 */
@Service
public class SupercardCouponService implements CouponService {

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

		this.apiClient = restClientBuilder
			.requestFactory(new HttpComponentsClientHttpRequestFactory(
					HttpClients.custom().disableCookieManagement().disableContentCompression().build()))
			.build();

		this.objectMapper = objectMapper.copy();
		objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		this.supercardProperties = supercardProperties;
	}

	/**
	 * Activates all available SuperCard digital coupons by first deactivating existing
	 * active coupons, then activating eligible inactive coupons based on filtering
	 * criteria.
	 * @param sessionCookies authentication cookies from browser session
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

			// Deactivate all ACTIVE digital coupons
			List<DigitalCoupon> activeCoupons = digitalCoupons.stream()
				.filter(item -> "ACTIVE".equals(item.status()))
				.toList();
			deactivateDigitalCoupons(activeCoupons, webapiBearerToken, userAgent, language);

			// Activate all OPEN digital coupons
			digitalCoupons = fetchDigitalCoupons(webapiBearerToken, userAgent, language);
			List<DigitalCoupon> inactiveCoupons = Stream
				.concat(digitalCoupons.stream().filter(item -> item.textDiscountAmount().contains("5 Rappen")),
						digitalCoupons.stream()
							.filter(item -> "OPEN".equals(item.status()))
							.filter(this::filterProductTypes)
							.filter(item -> "retail".equals(item.shop())))
				.limit(20)
				.toList();

			activateDigitalCoupons(inactiveCoupons, webapiBearerToken, userAgent, language);

			digitalCoupons = fetchDigitalCoupons(webapiBearerToken, userAgent, language);
			List<CouponDetail> activationResults = digitalCoupons.stream()
				.filter(item -> "ACTIVE".equals(item.status()))
				.map(item -> new CouponDetail(item.textDescription(), item.code(), true, item.textDiscountAmount()))
				.toList();
			int successfulActivations = activationResults.size();
			int failedActivations = digitalCoupons.size() - successfulActivations;

			logActivationSummary(successfulActivations, failedActivations, digitalCoupons.size());

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
	 * @return The extracted JWT token as a String.
	 * @throws JsonProcessingException if the API response cannot be parsed.
	 * @throws CouponBoosterException if the API call fails or returns an unexpected
	 * status.
	 */
	public String extractJwtToken(List<Cookie> sessionCookies, String userAgent, String language)
			throws JsonProcessingException {
		String cookieHeader = buildCookieHeader(sessionCookies);

		ResponseEntity<String> configResponse = apiClient.get()
			.uri(supercardProperties.urls().configUrl())
			.header(HttpHeaders.USER_AGENT, userAgent)
			.header(HttpHeaders.ACCEPT_LANGUAGE, language)
			.accept(APPLICATION_JSON)
			.header(USER_AGENT, userAgent)
			.header(ACCEPT_LANGUAGE, language)
			.header(COOKIE, cookieHeader)
			.header(REFERER, supercardProperties.urls().configUrlReferer())
			.retrieve()
			.toEntity(String.class);

		if (configResponse.getStatusCode() != HttpStatus.OK
				|| configResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CouponBoosterException("JWT token extraction failed.");
		}

		JsonNode rootNode = objectMapper.readTree(configResponse.getBody());
		JsonNode jwtToken = rootNode.path("jwtToken");
		return jwtToken.asText();
	}

	/**
	 * Builds a semicolon-separated cookie header string from a list of cookies.
	 */
	private String buildCookieHeader(final List<Cookie> sessionCookies) {
		return sessionCookies.stream()
			.map(cookie -> cookie.name + "=" + cookie.value)
			.collect(Collectors.joining("; "));
	}

	/**
	 * Filters a list of cookies to retain only those relevant for the target domain.
	 */
	private List<Cookie> filterDomainSpecificCookies(final List<Cookie> allCookies, final String targetDomain) {
		return allCookies.stream()
			.filter(cookie -> cookie.domain.startsWith(".") || cookie.domain.startsWith(targetDomain))
			.toList();
	}

	/**
	 * Fetches the complete list of digital coupons from the Supercard API.
	 */
	private List<DigitalCoupon> fetchDigitalCoupons(String webapiBearerToken, String userAgent, String language)
			throws JsonProcessingException {

		ResponseEntity<String> collectionResponse = apiClient.get()
			.uri(supercardProperties.urls().couponsUrl())
			.header(HttpHeaders.USER_AGENT, userAgent)
			.header(HttpHeaders.ACCEPT_LANGUAGE, language)
			.accept(APPLICATION_JSON)
			.header(AUTHORIZATION, "Bearer " + webapiBearerToken)
			.header(X_CLIENT_ID, X_CLIENT_ID_VALUE)
			.retrieve()
			.toEntity(String.class);

		if (collectionResponse.getStatusCode() != HttpStatus.OK
				|| collectionResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CouponBoosterException("Digital bons retrieval failed.");
		}

		List<DigitalCoupon> digitalCouponCollection = new ArrayList<>();

		JsonNode rootNode = objectMapper.readTree(collectionResponse.getBody());

		JsonNode digitalCoupons = rootNode.path("dc");

		if (digitalCoupons.isArray()) {
			for (JsonNode coupon : digitalCoupons) {
				String code = coupon.path("code").asText();
				String status = coupon.path("status").asText();
				String shop = coupon.path("formatIdMain").asText();
				String textDescription = coupon.path("textDescription").asText();
				String textDiscountAmount = coupon.path("textDiscountAmount").asText();

				boolean isNew = coupon.path("isNew").asBoolean();
				boolean isRecommendation = coupon.path("isRecommendation").asBoolean();
				boolean hasLogoProduct = !"none".equals(coupon.path("logoProduct").asText("none"));

				List<String> productTypes = new ArrayList<>();
				JsonNode productTypeArray = coupon.path("productTypes");
				if (productTypeArray.isArray()) {
					for (JsonNode productType : productTypeArray) {
						productTypes.add(productType.asText());
					}
				}

				LocalDateTime endDate = LocalDateTime.parse(coupon.path("endDate").asText(),
						DateTimeFormatter.ISO_DATE_TIME);

				digitalCouponCollection.add(new DigitalCoupon(code, status, shop, isNew, isRecommendation,
						hasLogoProduct, productTypes, endDate, textDescription, textDiscountAmount));
				log.info("Found digital coupon: {} - {}", code, status);
			}
		}

		return digitalCouponCollection;
	}

	boolean filterProductTypes(DigitalCoupon coupon) {
		List<String> includeList = List.of("39" // Aktuelles Bonheft
		// ,"20" // Baby, Kind
		// , "11" // Beauty
		// ,"23" // Bijouterie, Bekleidung
				, "01" // Bio, Nachhaltigkeit
				// ,"17" // Blumen, Pflanzen
				, "07" // Brot, Backwaren
				// , "12" // Drogerie
				// ,"18" // Elektronik, Büro
				, "09" // Fertiggerichte, Tiefkühlprodukte
				, "04" // Fleisch, Fisch
				// ,"19" // Freizeit, Sport
				, "03" // Früchte, Gemüse
				// ,"16" // Garten, Handwerk
				// , "06" // Getränke, alkoholisch
				, "05" // Getränke, nicht alkoholisch
				, "14" // Haushalt, Küche, Wohnen
				, "02" // Milchprodukte, Eier
				// ,"41" // Reisen, Ferien
				, "08" // Snacks, Süsses
				// ,"37" // Spezielle Ernährungsformen
				, "40" // Super Bons
				// ,"30" // Superpunkteangebote
				// ,"38" // Take-Away, Menüs
				// ,"21" // Tierwelt
				, "31" // Treibstoff, Fahrzeugbedarf
				, "36" // Vorräte
		);

		for (String productType : coupon.productTypes()) {
			if (!includeList.contains(productType)) {
				return false;
			}
		}
		return true;
	}

	private void deactivateDigitalCoupons(List<DigitalCoupon> activeCoupons, String webapiBearerToken, String userAgent,
			String language) throws JsonProcessingException {

		List<String> couponCodes = activeCoupons.stream().map(DigitalCoupon::code).toList();

		if (couponCodes.isEmpty()) {
			log.info("No active coupons to deactivate.");
			return;
		}

		ResponseEntity<String> activationResponse = apiClient.put()
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

		if (activationResponse.getStatusCode() != HttpStatus.OK
				|| activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CouponBoosterException("Digital coupon deactivation failed.");
		}
	}

	private void activateDigitalCoupons(List<DigitalCoupon> inactiveCoupons, String webapiBearerToken, String userAgent,
			String language) throws JsonProcessingException {

		List<String> couponCodes = inactiveCoupons.stream().map(DigitalCoupon::code).toList();

		if (couponCodes.isEmpty()) {
			log.info("No inactive coupons to activate.");
			return;
		}

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

		if (activationResponse.getStatusCode() != HttpStatus.OK
				|| activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CouponBoosterException("Digital coupon activation failed.");
		}
	}

	private void logActivationSummary(int successCount, int failureCount, int totalAttempts) {
		if (successCount > 0) {
			int successRate = (successCount * 100) / totalAttempts;
			log.info("Successfully activated {} of {} coupons ({}% success rate)", successCount, totalAttempts,
					successRate);
		}
		else {
			log.warn("No coupons were successfully activated");
		}

		if (failureCount > 0) {
			log.warn("{} coupon activations failed", failureCount);
		}
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
