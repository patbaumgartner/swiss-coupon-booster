package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.properties.CumulusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CumulusCouponServiceTest {

	@Mock
	private RestClient.Builder restClientBuilder;

	@Mock
	private RestClient restClient;

	@SuppressWarnings("rawtypes")
	@Mock
	private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

	@Mock
	private CumulusProperties cumulusProperties;

	@Mock
	private CumulusProperties.Urls urls;

	private CumulusCouponService cumulusCouponService;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		lenient().when(cumulusProperties.urls()).thenReturn(urls);
		lenient().when(urls.baseUrl()).thenReturn("https://api.migros.ch");
		lenient().when(urls.couponsEndpoint()).thenReturn("https://api.migros.ch/coupons");
		lenient().when(urls.couponsReferer()).thenReturn("https://api.migros.ch/dashboard");
		lenient().when(restClientBuilder.baseUrl("https://api.migros.ch")).thenReturn(restClientBuilder);
		lenient().when(restClientBuilder.build()).thenReturn(restClient);
		lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);

		cumulusCouponService = new CumulusCouponService(restClientBuilder, cumulusProperties);
	}

	@Test
	void activateAllAvailableCoupons_withNoCookies_shouldReturnEmptyResult() {
		// Given
		List<Cookie> sessionCookies = Collections.emptyList();

		// When
		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(sessionCookies, "userAgent",
				"en");

		// Then
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
	}

	@Test
	void activateAllAvailableCoupons_withValidCookies_shouldReturnErrorResult() {
		// Given - cookies without CSRF token will cause the service to fail
		List<Cookie> sessionCookies = List.of(new Cookie("test-cookie", "test-value").setDomain(".migros.ch"));

		// When - The service will try to extract CSRF token and fail, causing exception
		// handling
		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(sessionCookies, "userAgent",
				"en");

		// Then - Should return error result due to missing CSRF token
		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).hasSize(1);
		assertThat(result.details().get(0).couponId()).isEqualTo("unknown");
		assertThat(result.details().get(0).success()).isFalse();
		assertThat(result.details().get(0).message()).contains("Process failed");
	}

}
