package com.patbaumgartner.couponbooster.coop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupercardCouponServiceTest {

	@Mock
	private SupercardProperties supercardProperties;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private RestClient.Builder restClientBuilder;

	@Mock
	private RestClient restClient;

	private SupercardCouponService supercardCouponService;

	@BeforeEach
	void setUp() {
		when(restClientBuilder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(restClientBuilder);
		when(restClientBuilder.build()).thenReturn(restClient);
		when(objectMapper.copy()).thenReturn(objectMapper);

		supercardCouponService = new SupercardCouponService(restClientBuilder, objectMapper, supercardProperties);
	}

	@Test
	void contextLoads() {
		assertThat(supercardCouponService).isNotNull();
	}

	@Test
	void activateAllAvailableCoupons_withNullCookies_returnsEmptyResult() {
		// when
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(null);

		// then
		assertThat(result).isNotNull();
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

	@Test
	void activateAllAvailableCoupons_withEmptyCookies_returnsEmptyResult() {
		// when
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(Collections.emptyList());

		// then
		assertThat(result).isNotNull();
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

	@Test
	void activateAllAvailableCoupons_withNoDomainMatchingCookies_returnsEmptyResult() {
		// given
		List<Cookie> cookies = List.of(new Cookie("name", "value").setDomain("another-domain.com"));

		// when
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(cookies);

		// then
		assertThat(result).isNotNull();
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

	@Test
	void activateAllAvailableCoupons_withValidCookiesButNoCoupons_returnsEmptyResult() throws Exception {
		// given
		var urls = new SupercardProperties.Urls("http://base.url", "http://config.url", "http://referer.url",
				"http://coupons.url", "http://activation.url", "http://deactivation.url");
		var browser = new SupercardProperties.Browser("user-agent", "lang");
		when(supercardProperties.urls()).thenReturn(urls);
		when(supercardProperties.browser()).thenReturn(browser);

		List<Cookie> cookies = List.of(new Cookie("name", "value").setDomain(".supercard.ch"));

		// --- Mocking RestClient Fluent API ---
		RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
		RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
		RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

		when(restClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

		// First call for JWT, second for coupons
		when(responseSpec.toEntity(String.class))
			.thenReturn(new ResponseEntity<>("{\"jwtToken\":\"dummy-jwt\"}", HttpStatus.OK))
			.thenReturn(new ResponseEntity<>("{\"dc\":[]}", HttpStatus.OK));

		// --- Mocking ObjectMapper ---
		ObjectMapper realObjectMapper = new ObjectMapper();
		when(objectMapper.readTree("{\"jwtToken\":\"dummy-jwt\"}"))
			.thenReturn(realObjectMapper.readTree("{\"jwtToken\":\"dummy-jwt\"}"));
		when(objectMapper.readTree("{\"dc\":[]}")).thenReturn(realObjectMapper.readTree("{\"dc\":[]}"));

		// when
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(cookies);

		// then
		assertThat(result).isNotNull();
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

}
