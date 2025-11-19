package com.patbaumgartner.couponbooster.migros.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.migros.config.MigrosConstants;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.model.CouponDetail;
import com.patbaumgartner.couponbooster.migros.model.CouponInfo;
import com.patbaumgartner.couponbooster.migros.properties.CumulusProperties;
import com.patbaumgartner.couponbooster.service.AbstractCouponService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.patbaumgartner.couponbooster.migros.config.MigrosConstants.Cookies.AUTHENTICATION_DOMAIN;
import static com.patbaumgartner.couponbooster.migros.config.MigrosConstants.Cookies.CSRF_COOKIE_NAME;
import static com.patbaumgartner.couponbooster.migros.config.MigrosConstants.HttpHeaders.CSRF_TOKEN_HEADER;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Service for managing Migros Cumulus digital coupons through the Migros API.
 * <p>
 * This service handles the entire lifecycle of coupon management, including:
 * <ul>
 * <li>Fetching all available and activated digital coupons.</li>
 * <li>Activating all available, inactive coupons.</li>
 * </ul>
 * It interacts directly with the Migros Cumulus web API using a {@link RestClient}.
 *
 * @see CouponService
 * @see CumulusProperties
 */
@Service
public final class CumulusCouponService extends AbstractCouponService {

	private static final Logger log = LoggerFactory.getLogger(CumulusCouponService.class);

	private final RestClient apiClient;

	private final CumulusProperties configuration;

	/**
	 * Constructs a new {@code CumulusCouponService} with the specified dependencies.
	 * @param restClientBuilder the builder for creating the {@link RestClient} instance
	 * @param configuration the configuration properties for the Cumulus API
	 */
	public CumulusCouponService(final RestClient.Builder restClientBuilder, final CumulusProperties configuration) {
		this.configuration = configuration;
		this.apiClient = restClientBuilder.baseUrl(configuration.urls().baseUrl()).build();
	}

	/**
	 * Activates all available Cumulus digital coupons.
	 * <p>
	 * This method orchestrates the entire coupon activation process:
	 * <ol>
	 * <li>Fetches all available and activated digital coupons.</li>
	 * <li>Filters for inactive coupons.</li>
	 * <li>Activates each inactive coupon individually.</li>
	 * </ol>
	 * @param sessionCookies authentication cookies from browser session
	 * @param userAgent the user agent of the browser
	 * @param language the language of the browser
	 * @return a {@link CouponActivationResult} containing activation statistics and
	 * coupon details
	 */
	@Override
	public CouponActivationResult activateAllAvailableCoupons(final List<Cookie> sessionCookies, String userAgent,
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

			var availableCoupons = fetchAvailableCoupons(filteredCookies, userAgent, language);
			return processCouponActivations(availableCoupons, filteredCookies, userAgent, language);
		}
		catch (Exception exception) {
			log.error("Coupon activation process failed unexpectedly: {}", exception.getMessage(), exception);
			return new CouponActivationResult(0, 1, List
				.of(new CouponDetail("System Error", "unknown", false, "Process failed: " + exception.getMessage())));
		}
	}

	private List<CouponInfo> fetchAvailableCoupons(final List<Cookie> sessionCookies, String userAgent,
			String language) {
		String cookieHeader = buildCookieHeader(sessionCookies);

		try {
			log.debug("Fetching available coupons from API");

			CouponsResponse apiResponse = this.apiClient.get()
				.uri(configuration.urls().couponsEndpoint())
				.accept(APPLICATION_JSON)
				.header(HttpHeaders.USER_AGENT, userAgent)
				.header(HttpHeaders.ACCEPT_LANGUAGE, language)
				.header(COOKIE, cookieHeader)
				.header(REFERER, this.configuration.urls().couponsReferer())
				.header(CSRF_TOKEN_HEADER, extractCsrfToken(sessionCookies))
				.retrieve()
				.body(CouponsResponse.class);

			if (apiResponse == null) {
				return List.of();
			}

			return Stream.of(apiResponse.available(), apiResponse.activated())
				.filter(Objects::nonNull)
				.flatMap(List::stream)
				.filter(Objects::nonNull)
				.map(this::mapToCouponInfo)
				.toList();
		}
		catch (Exception exception) {
			log.error("Failed to fetch coupons from API: {}", exception.getMessage(), exception);
			throw new CouponBoosterException("Failed to fetch coupons", exception);
		}
	}

	private CouponInfo mapToCouponInfo(RawCoupon rawCoupon) {
		String description = rawCoupon.subtitle != null ? rawCoupon.subtitle : rawCoupon.disclaimer;
		boolean isActivated = "ACTIVATED".equalsIgnoreCase(rawCoupon.status);
		return new CouponInfo(rawCoupon.id, rawCoupon.name, description, rawCoupon.validTo, isActivated);
	}

	private CouponActivationResult processCouponActivations(final List<CouponInfo> allCoupons,
			final List<Cookie> sessionCookies, String userAgent, String language) {

		var inactiveCoupons = allCoupons.stream().filter(coupon -> !coupon.activated()).toList();

		int alreadyActivatedCount = allCoupons.size() - inactiveCoupons.size();

		if (log.isInfoEnabled()) {
			log.info("Found {} total coupons: {} already activated, {} pending activation", allCoupons.size(),
					alreadyActivatedCount, inactiveCoupons.size());
		}

		if (inactiveCoupons.isEmpty()) {
			log.info("All available coupons are already activated");
			return new CouponActivationResult(0, 0, List.of());
		}

		var activationResults = inactiveCoupons.stream()
			.map(coupon -> activateSingleCoupon(coupon.id(), sessionCookies, userAgent, language))
			.toList();

		int successfulActivations = (int) activationResults.stream().filter(CouponDetail::success).count();
		int failedActivations = activationResults.size() - successfulActivations;

		logActivationSummary(successfulActivations, failedActivations, activationResults.size());

		return new CouponActivationResult(successfulActivations, failedActivations, activationResults);
	}

	private CouponDetail activateSingleCoupon(final String couponId, final List<Cookie> sessionCookies,
			String userAgent, String language) {
		try {
			if (couponId == null || couponId.isBlank()) {
				log.warn("Skipping activation - coupon ID is null or empty");
				return new CouponDetail("Coupon", couponId, false, "Invalid coupon ID");
			}

			log.debug("Attempting to activate coupon: {}", couponId);

			String cookieHeader = buildCookieHeader(sessionCookies);
			String csrfToken = extractCsrfToken(sessionCookies);

			var activationRequest = this.apiClient.post()
				.uri(configuration.urls().activationEndpoint())
				.header(HttpHeaders.USER_AGENT, userAgent)
				.header(HttpHeaders.ACCEPT_LANGUAGE, language)
				.header(ACCEPT, APPLICATION_JSON_VALUE)
				.header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
				.header(COOKIE, cookieHeader)
				.header(REFERER, this.configuration.urls().couponsEndpoint());

			if (csrfToken != null && !csrfToken.isBlank()) {
				activationRequest = activationRequest.header(MigrosConstants.HttpHeaders.CSRF_TOKEN_HEADER, csrfToken);
			}
			else {
				log.warn("CSRF token not found in session cookies, proceeding without token");
			}

			var apiResponse = activationRequest.body(Map.of("id", couponId)).retrieve().toEntity(String.class);

			if (apiResponse.getStatusCode().is2xxSuccessful()) {
				log.debug("Coupon activation successful: {}", couponId);
				return new CouponDetail("Coupon", couponId, true, "Activation completed successfully");
			}
			else {
				log.warn("Coupon activation failed for {}: HTTP status {}", couponId,
						apiResponse.getStatusCode().value());
				return new CouponDetail("Coupon", couponId, false, "HTTP " + apiResponse.getStatusCode().value());
			}
		}
		catch (HttpClientErrorException exception) {
			log.error("Failed to activate coupon {}: {}", couponId, exception.getMessage(), exception);
			return new CouponDetail("Coupon", couponId, false, exception.getMessage());
		}
	}

	private String extractCsrfToken(final List<Cookie> sessionCookies) {
		return sessionCookies.stream()
			.filter(cookie -> CSRF_COOKIE_NAME.equals(cookie.name))
			.map(cookie -> cookie.value)
			.filter(value -> value != null && !value.isBlank())
			.findFirst()
			.orElseThrow(() -> new CouponBoosterException(
					"CSRF token '%s' not found or empty in session cookies".formatted(CSRF_COOKIE_NAME)));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawCoupon(@JsonProperty("id") String id, @JsonProperty("name") String name,
			@JsonProperty("subtitle") String subtitle, @JsonProperty("disclaimer") String disclaimer,
			@JsonProperty("validTo") String validTo, @JsonProperty("status") String status) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CouponsResponse(@JsonProperty("available") List<RawCoupon> available,
			@JsonProperty("activated") List<RawCoupon> activated, @JsonProperty("preview") List<RawCoupon> preview,
			@JsonProperty("redeemed") List<RawCoupon> redeemed, @JsonProperty("partner") List<RawCoupon> partner) {
	}

}
