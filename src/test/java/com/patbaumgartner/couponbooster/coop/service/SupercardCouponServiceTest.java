package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(SupercardCouponService.class)
class SupercardCouponServiceTest {

	@Autowired
	private SupercardCouponService supercardCouponService;

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private SupercardProperties supercardProperties;

	@MockitoBean
	private SupercardProperties.Urls urls;

	@BeforeEach
	void setUp() {
		when(supercardProperties.urls()).thenReturn(urls);
	}

	@Test
	void activateAllAvailableCoupons_withNoCookies_shouldReturnEmptyResult() {
		// Given
		List<Cookie> sessionCookies = Collections.emptyList();

		// When
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(sessionCookies, "userAgent",
				"en");

		// Then
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

	@Test
	void activateAllAvailableCoupons_withValidCookies_shouldActivateCoupons() throws Exception {
		// Given
		List<Cookie> sessionCookies = List.of(new Cookie("test-cookie", "test-value").setDomain(".coop.ch"));

		when(supercardProperties.couponFilter())
			.thenReturn(new SupercardProperties.CouponFilter(Collections.emptyList()));
		when(supercardProperties.urls()).thenReturn(urls);
		when(urls.configUrl()).thenReturn("https://api.coop.ch/config");
		when(urls.couponsUrl()).thenReturn("https://api.coop.ch/coupons");
		when(urls.couponsDeactivationUrl()).thenReturn("https://api.coop.ch/coupons/deactivation");
		when(urls.couponsActivationUrl()).thenReturn("https://api.coop.ch/coupons/activation");

		String jwtTokenResponse = "{\"jwtToken\":\"test-token\"}";
		server.expect(requestTo("https://api.coop.ch/config"))
			.andRespond(withSuccess(jwtTokenResponse, MediaType.APPLICATION_JSON));

		String couponsResponse = "{\"dc\":[{\"code\":\"c1\",\"status\":\"ACTIVE\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon\",\"textDiscountAmount\":\"10%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]},{\"code\":\"c2\",\"status\":\"OPEN\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon 2\",\"textDiscountAmount\":\"20%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]}]}";
		String deactivatedCouponsResponse = "{\"dc\":[{\"code\":\"c1\",\"status\":\"OPEN\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon\",\"textDiscountAmount\":\"10%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]},{\"code\":\"c2\",\"status\":\"OPEN\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon 2\",\"textDiscountAmount\":\"20%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]}]}";
		String activatedCouponsResponse = "{\"dc\":[{\"code\":\"c1\",\"status\":\"ACTIVE\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon\",\"textDiscountAmount\":\"10%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]},{\"code\":\"c2\",\"status\":\"ACTIVE\",\"endDate\":\"2025-12-31T23:59:59\",\"formatIdMain\":\"retail\",\"textDescription\":\"Test coupon 2\",\"textDiscountAmount\":\"20%\",\"isNew\":false,\"isRecommendation\":false,\"logoProduct\":\"none\",\"productTypes\":[]}]}";

		// First call - fetch coupons (initial state)
		server.expect(requestTo("https://api.coop.ch/coupons"))
			.andRespond(withSuccess(couponsResponse, MediaType.APPLICATION_JSON));

		// Second call - deactivate active coupons
		server.expect(requestTo("https://api.coop.ch/coupons/deactivation"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		// Third call - fetch coupons after deactivation
		server.expect(requestTo("https://api.coop.ch/coupons"))
			.andRespond(withSuccess(deactivatedCouponsResponse, MediaType.APPLICATION_JSON));

		// Fourth call - activate filtered coupons
		server.expect(requestTo("https://api.coop.ch/coupons/activation"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		// Fifth call - fetch final coupon state
		server.expect(requestTo("https://api.coop.ch/coupons"))
			.andRespond(withSuccess(activatedCouponsResponse, MediaType.APPLICATION_JSON));

		// When
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(sessionCookies, "userAgent",
				"en");

		// Then
		assertThat(result).isNotNull();
		server.verify();
	}

}
