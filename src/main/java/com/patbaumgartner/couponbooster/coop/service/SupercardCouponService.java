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
 * Service for managing Coop SuperCard digital coupons (bons) through API integration.
 * Handles authentication, coupon retrieval, activation, and deactivation of digital offers.
 * Uses JWT tokens for API authentication and manages coupon lifecycle operations.
 */
/**
 * Service for managing SuperCard digital coupons through the Coop API. Handles
 * authentication, coupon retrieval, activation, and deactivation operations. This service
 * interacts with the Coop SuperCard web API to automatically manage available digital
 * coupons for users.
 */
@Service
public class SupercardCouponService implements CouponService {

	private static final Logger log = LoggerFactory.getLogger(SupercardCouponService.class);

	private final SupercardProperties supercardProperties;

	private final ObjectMapper objectMapper;

	private final RestClient apiClient;

	/**
	 * Creates a new SuperCard coupon service with the given configuration.
	 * @param restClientBuilder builder for creating HTTP client instances
	 * @param objectMapper JSON object mapper for API request/response processing
	 * @param supercardProperties configuration properties for SuperCard API endpoints
	 */
	/**
	 * Creates a new SuperCard coupon service with configured HTTP client and JSON
	 * processing.
	 * @param restClientBuilder builder for creating REST client with custom configuration
	 * @param objectMapper Jackson object mapper for JSON serialization/deserialization
	 * @param supercardProperties configuration properties for API endpoints and browser
	 * settings
	 */
	public SupercardCouponService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			SupercardProperties supercardProperties) {

		this.apiClient = restClientBuilder
			.requestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.custom()
				.setUserAgent(USER_AGENT)
				.disableCookieManagement()
				.disableContentCompression()
				.build()))
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
	public CouponActivationResult activateAllAvailableCoupons(List<Cookie> sessionCookies) {

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

			String webapiBearerToken = extractJwtToken(filteredCookies);
			var digitalCoupons = fetchDigitalCoupons(webapiBearerToken);

			// Deactivate all ACTIVE digital coupons
			List<DigitalCoupon> activeCoupons = digitalCoupons.stream()
				.filter(item -> "ACTIVE".equals(item.status()))
				.toList();
			deactivateDigitalCoupons(activeCoupons, webapiBearerToken);

			// Activate all OPEN digital coupons
			digitalCoupons = fetchDigitalCoupons(webapiBearerToken);
			List<DigitalCoupon> inactiveCoupons = Stream
				.concat(digitalCoupons.stream().filter(item -> item.textDiscountAmount().contains("5 Rappen")),
						digitalCoupons.stream()
							.filter(item -> "OPEN".equals(item.status()))
							.filter(this::filterProductTypes)
							.filter(item -> "retail".equals(item.shop())))
				.limit(20)
				.toList();

			activateDigitalCoupons(inactiveCoupons, webapiBearerToken);

			digitalCoupons = fetchDigitalCoupons(webapiBearerToken);
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

	public String extractJwtToken(List<Cookie> sessionCookies) throws JsonProcessingException {
		String cookieHeader = buildCookieHeader(sessionCookies);

		ResponseEntity<String> configResponse = apiClient.get()
			.uri(supercardProperties.urls().configUrl())
			.accept(APPLICATION_JSON)
			.header(USER_AGENT, supercardProperties.browser().userAgent())
			.header(ACCEPT_LANGUAGE, supercardProperties.browser().language())
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

	private String buildCookieHeader(final List<Cookie> sessionCookies) {
		return sessionCookies.stream()
			.map(cookie -> cookie.name + "=" + cookie.value)
			.collect(Collectors.joining("; "));
	}

	private List<Cookie> filterDomainSpecificCookies(final List<Cookie> allCookies, final String targetDomain) {
		return allCookies.stream()
			.filter(cookie -> cookie.domain.startsWith(".") || cookie.domain.startsWith(targetDomain))
			.toList();
	}

	private List<DigitalCoupon> fetchDigitalCoupons(String webapiBearerToken) throws JsonProcessingException {

		ResponseEntity<String> collectionResponse = apiClient.get()
			.uri(supercardProperties.urls().couponsUrl())
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
				boolean hasLogoProduct = !coupon.path("logoProduct").asText("none").equals("none");

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

	private void deactivateDigitalCoupons(List<DigitalCoupon> activeCoupons, String webapiBearerToken)
			throws JsonProcessingException {

		List<String> couponCodes = activeCoupons.stream().map(DigitalCoupon::code).toList();

		ResponseEntity<String> activationResponse = apiClient.put()
			.uri(supercardProperties.urls().couponsDeactivationUrl())
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

	private void activateDigitalCoupons(List<DigitalCoupon> inactiveCoupons, String webapiBearerToken)
			throws JsonProcessingException {

		List<String> couponCodes = inactiveCoupons.stream().map(DigitalCoupon::code).toList();

		ResponseEntity<String> activationResponse = apiClient.put()
			.uri(supercardProperties.urls().couponsActivationUrl())
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

	private record DigitalCouponCollection(List<String> codes) {
	}

	private record DigitalCoupon(String code, String status, String shop, boolean isNew, boolean isRecommendation,
			boolean hasLogoProduct, List<String> productTypes, LocalDateTime endDate, String textDescription,
			String textDiscountAmount) {
	}

}
