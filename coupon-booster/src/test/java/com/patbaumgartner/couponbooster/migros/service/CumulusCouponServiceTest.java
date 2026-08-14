package com.patbaumgartner.couponbooster.migros.service;

import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.model.CouponDetail;
import com.patbaumgartner.couponbooster.migros.properties.CumulusProperties;
import com.patbaumgartner.couponbooster.model.SessionCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(CumulusCouponService.class)
class CumulusCouponServiceTest {

	private static final String COUPONS_URL = "https://account.migros.ch/ma/api/user/cumulus/coupon";

	private static final String ACTIVATION_URL = COUPONS_URL + "/activation";

	private static final List<SessionCookie> COOKIES = List.of(new SessionCookie("CSRF", "csrf-token", ".migros.ch"),
			new SessionCookie("session", "abc", "account.migros.ch"));

	@Autowired
	private CumulusCouponService cumulusCouponService;

	@Autowired
	private MockRestServiceServer server;

	@MockitoBean
	private CumulusProperties cumulusProperties;

	@MockitoBean
	private CumulusProperties.Urls urls;

	@MockitoBean
	private CumulusProperties.Api api;

	@BeforeEach
	void setUp() {
		when(cumulusProperties.urls()).thenReturn(urls);
		when(urls.couponsEndpoint()).thenReturn(COUPONS_URL);
		when(urls.couponsReferer()).thenReturn("https://account.migros.ch/cumulus/dashboard");
		when(urls.activationEndpoint()).thenReturn(ACTIVATION_URL);
		when(cumulusProperties.api()).thenReturn(api);
		when(api.requestDelay()).thenReturn(Duration.ZERO);
	}

	private void expectCoupons(String body) {
		server.expect(requestTo(COUPONS_URL))
			.andExpect(header("X-CSRF-TOKEN", "csrf-token"))
			.andExpect(header("Cookie", "CSRF=csrf-token; session=abc"))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void withoutCookies_doesNotCallTheApi() {
		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(List.of(), "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		server.verify();
	}

	@Test
	void cookiesForAnotherRetailerAreNotSentToTheCumulusApi() {
		var coopCookies = List.of(new SessionCookie("datadome", "x", ".supercard.ch"));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(coopCookies, "ua", "de");

		assertThat(result.successCount()).isZero();
		server.verify();
	}

	@Test
	void activatesEveryCouponThatIsNotYetActivated() {
		expectCoupons("""
				{"available":[{"id":"c1","name":"One","subtitle":"s1","validTo":"2026-01-01","status":"AVAILABLE"},
				              {"id":"c2","name":"Two","subtitle":"s2","validTo":"2026-01-01","status":"AVAILABLE"}],
				 "activated":[{"id":"c3","name":"Three","subtitle":"s3","validTo":"2026-01-01","status":"ACTIVATED"}]}
				""");
		server.expect(requestTo(ACTIVATION_URL))
			.andExpect(header("X-CSRF-TOKEN", "csrf-token"))
			.andExpect(content().json("{\"id\":\"c1\"}"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ACTIVATION_URL))
			.andExpect(content().json("{\"id\":\"c2\"}"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(2);
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).extracting(CouponDetail::couponId).containsExactly("c1", "c2");
		server.verify();
	}

	@Test
	void alreadyActivatedCouponsAreNotReactivated() {
		expectCoupons("""
				{"activated":[{"id":"c1","name":"One","validTo":"2026-01-01","status":"ACTIVATED"}]}""");

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
		server.verify();
	}

	@Test
	void oneFailingCouponDoesNotAbortTheRestOfTheBatch() {
		expectCoupons("""
				{"available":[{"id":"bad","name":"Bad","validTo":"2026-01-01","status":"AVAILABLE"},
				              {"id":"good","name":"Good","validTo":"2026-01-01","status":"AVAILABLE"}]}""");
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).extracting(CouponDetail::couponId).containsExactly("bad", "good");
		server.verify();
	}

	@Test
	void missingCsrfCookieIsReportedInsteadOfThrowing() {
		var noCsrf = List.of(new SessionCookie("session", "abc", ".migros.ch"));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(noCsrf, "ua", "de");

		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).singleElement().satisfies(detail -> {
			assertThat(detail.couponName()).isEqualTo("System Error");
			assertThat(detail.message()).contains("Process failed");
		});
	}

	@Test
	void aFailedCouponFetchIsReportedInsteadOfThrowing() {
		server.expect(requestTo(COUPONS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void anEmptyCouponResponseYieldsNoActivations() {
		expectCoupons("{}");

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.details()).isEmpty();
		server.verify();
	}

	@Test
	void couponDescriptionFallsBackToTheDisclaimerWhenNoSubtitleIsGiven() {
		expectCoupons("""
				{"available":[{"id":"c1","name":"One","disclaimer":"legal text","validTo":"2026-01-01",
				               "status":"AVAILABLE"}]}""");
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void nullCookieListIsTreatedAsNoCookies() {
		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(null, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		server.verify();
	}

	@Test
	void aConfiguredDelayIsAppliedBetweenActivations() {
		when(api.requestDelay()).thenReturn(Duration.ofMillis(5));
		expectCoupons("""
				{"available":[{"id":"c1","name":"One","validTo":"2026-01-01","status":"AVAILABLE"}]}""");
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void aBlankCsrfCookieIsTreatedAsMissing() {
		var blankCsrf = List.of(new SessionCookie("CSRF", "  ", ".migros.ch"));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(blankCsrf, "ua", "de");

		assertThat(result.failureCount()).isEqualTo(1);
	}

	@Test
	void aServerErrorDuringActivationIsRecordedForThatCouponOnly() {
		expectCoupons("""
				{"available":[{"id":"c1","name":"One","validTo":"2026-01-01","status":"AVAILABLE"},
				              {"id":"c2","name":"Two","validTo":"2026-01-01","status":"AVAILABLE"}]}""");
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		server.expect(requestTo(ACTIVATION_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		CouponActivationResult result = cumulusCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		assertThat(result.failureCount()).isEqualTo(1);
		server.verify();
	}

}
