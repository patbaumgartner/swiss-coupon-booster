package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.model.CouponDetail;
import com.patbaumgartner.couponbooster.model.SessionCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(SupercardCouponService.class)
class SupercardCouponServiceTest {

	private static final String CONFIG_URL = "https://api.coop.ch/config";

	private static final String COUPONS_URL = "https://api.coop.ch/coupons";

	private static final String ACTIVATE_URL = "https://api.coop.ch/coupons/activation";

	private static final String DEACTIVATE_URL = "https://api.coop.ch/coupons/deactivation";

	private static final List<SessionCookie> COOKIES = List.of(new SessionCookie("session", "value", ".supercard.ch"));

	@Autowired
	private SupercardCouponService supercardCouponService;

	@Autowired
	private MockRestServiceServer server;

	@MockitoBean
	private SupercardProperties supercardProperties;

	@MockitoBean
	private SupercardProperties.Urls urls;

	@BeforeEach
	void setUp() {
		when(supercardProperties.urls()).thenReturn(urls);
		when(urls.configUrl()).thenReturn(CONFIG_URL);
		when(urls.couponsUrl()).thenReturn(COUPONS_URL);
		when(urls.couponsActivationUrl()).thenReturn(ACTIVATE_URL);
		when(urls.couponsDeactivationUrl()).thenReturn(DEACTIVATE_URL);
	}

	private void givenFilter(int maxActive, String shop, String marker, List<String> productTypes) {
		when(supercardProperties.couponFilter())
			.thenReturn(new SupercardProperties.CouponFilter(maxActive, shop, marker, productTypes));
	}

	private static String coupon(String code, String status, String shop, String discount, String... productTypes) {
		String types = productTypes.length == 0 ? "" : "\"" + String.join("\",\"", productTypes) + "\"";
		return """
				{"code":"%s","status":"%s","formatIdMain":"%s","textDescription":"desc",\
				"textDiscountAmount":"%s","productTypes":[%s]}""".formatted(code, status, shop, discount, types);
	}

	private static String catalogue(String... coupons) {
		return "{\"dc\":[" + String.join(",", coupons) + "]}";
	}

	private void expectJwt() {
		server.expect(requestTo(CONFIG_URL))
			.andRespond(withSuccess("{\"jwtToken\":\"test-token\"}", MediaType.APPLICATION_JSON));
	}

	private void expectCoupons(String body) {
		server.expect(requestTo(COUPONS_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void withoutCookies_doesNotCallTheApi() {
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(List.of(), "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).isEmpty();
		server.verify();
	}

	@Test
	void cookiesForAnotherRetailerAreNotSentToTheSupercardApi() {
		var migrosCookies = List.of(new SessionCookie("CSRF", "token", ".migros.ch"));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(migrosCookies, "ua", "de");

		assertThat(result.successCount()).isZero();
		server.verify();
	}

	@Test
	void activatesEligibleCouponsAndReportsTheConfirmedOnes() {
		givenFilter(20, "retail", "5 Rappen", List.of("03"));
		expectJwt();
		expectCoupons(catalogue(coupon("active1", "ACTIVE", "retail", "10%", "03"),
				coupon("open1", "OPEN", "retail", "20%", "03")));

		server.expect(requestTo(DEACTIVATE_URL))
			.andExpect(content().json("{\"codes\":[\"active1\"]}"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		expectCoupons(catalogue(coupon("active1", "OPEN", "retail", "10%", "03"),
				coupon("open1", "OPEN", "retail", "20%", "03")));

		server.expect(requestTo(ACTIVATE_URL))
			.andExpect(header("X-Client-Id", "WEB_SUPERCARD"))
			.andExpect(content().json("{\"codes\":[\"active1\",\"open1\"]}"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		expectCoupons(catalogue(coupon("active1", "ACTIVE", "retail", "10%", "03"),
				coupon("open1", "ACTIVE", "retail", "20%", "03")));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(2);
		assertThat(result.failureCount()).isZero();
		assertThat(result.details()).extracting(CouponDetail::couponId).containsExactly("active1", "open1");
		server.verify();
	}

	@Test
	void skipsCouponsFromAnotherShopChannelAndDisallowedProductTypes() {
		givenFilter(20, "retail", "5 Rappen", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("keep", "OPEN", "retail", "10%", "03"),
				coupon("wrongShop", "OPEN", "online", "10%", "03"), coupon("wrongType", "OPEN", "retail", "10%", "99"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);

		server.expect(requestTo(ACTIVATE_URL))
			.andExpect(content().json("{\"codes\":[\"keep\"]}", true))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		expectCoupons(catalogue(coupon("keep", "ACTIVE", "retail", "10%", "03"),
				coupon("wrongShop", "OPEN", "online", "10%", "03"),
				coupon("wrongType", "OPEN", "retail", "10%", "99")));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.details()).extracting(CouponDetail::couponId).containsExactly("keep");
		server.verify();
	}

	@Test
	void couponsCarryingTheDiscountMarkerBypassTheProductTypeFilter() {
		givenFilter(20, "retail", "5 Rappen", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("fuel", "OPEN", "online", "5 Rappen/Liter", "99"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);

		server.expect(requestTo(ACTIVATE_URL))
			.andExpect(content().json("{\"codes\":[\"fuel\"]}", true))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		expectCoupons(catalogue(coupon("fuel", "ACTIVE", "online", "5 Rappen/Liter", "99")));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void neverActivatesMoreThanTheConfiguredMaximum() {
		givenFilter(2, "retail", "", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("c1", "OPEN", "retail", "1%", "03"),
				coupon("c2", "OPEN", "retail", "2%", "03"), coupon("c3", "OPEN", "retail", "3%", "03"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);

		server.expect(requestTo(ACTIVATE_URL))
			.andExpect(content().json("{\"codes\":[\"c1\",\"c2\"]}", true))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		expectCoupons(catalogue);

		supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		server.verify();
	}

	@Test
	void couponsNotConfirmedActiveAreCountedAsFailures() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("c1", "OPEN", "retail", "1%", "03"),
				coupon("c2", "OPEN", "retail", "2%", "03"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);
		server.expect(requestTo(ACTIVATE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		expectCoupons(
				catalogue(coupon("c1", "ACTIVE", "retail", "1%", "03"), coupon("c2", "OPEN", "retail", "2%", "03")));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		assertThat(result.failureCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void malformedCouponEntriesDoNotAbortTheRun() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		// endDate missing entirely and productTypes absent: previously fatal.
		String catalogue = "{\"dc\":[{\"code\":\"c1\",\"status\":\"OPEN\",\"formatIdMain\":\"retail\","
				+ "\"textDescription\":\"d\",\"textDiscountAmount\":\"1%\"}]}";
		expectCoupons(catalogue);
		expectCoupons(catalogue);
		server.expect(requestTo(ACTIVATE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		expectCoupons("{\"dc\":[{\"code\":\"c1\",\"status\":\"ACTIVE\",\"formatIdMain\":\"retail\","
				+ "\"textDescription\":\"d\",\"textDiscountAmount\":\"1%\"}]}");

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void extractJwtToken_failsClearlyWhenDataDomeReturnsHtml() {
		server.expect(requestTo(CONFIG_URL)).andRespond(withSuccess("<html>blocked</html>", MediaType.TEXT_HTML));

		assertThatExceptionOfType(CouponBoosterException.class)
			.isThrownBy(() -> supercardCouponService.extractJwtToken(COOKIES, "ua", "de"))
			.withMessageContaining("HTML instead of JSON");
	}

	@Test
	void extractJwtToken_doesNotEchoTheResponseBodyWhenTheTokenIsMissing() {
		server.expect(requestTo(CONFIG_URL))
			.andRespond(withSuccess("{\"sessionSecret\":\"do-not-leak\"}", MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(CouponBoosterException.class)
			.isThrownBy(() -> supercardCouponService.extractJwtToken(COOKIES, "ua", "de"))
			.withMessageNotContaining("do-not-leak");
	}

	@Test
	void anApiFailureIsReportedInsteadOfThrowing() {
		givenFilter(20, "retail", "", List.of("03"));
		server.expect(requestTo(CONFIG_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).singleElement().extracting(CouponDetail::couponName).isEqualTo("System Error");
	}

	@Test
	void nullCookieListIsTreatedAsNoCookies() {
		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(null, "ua", "de");

		assertThat(result.successCount()).isZero();
		assertThat(result.failureCount()).isZero();
		server.verify();
	}

	@Test
	void aNonOkCouponFetchIsReportedInsteadOfThrowing() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		server.expect(requestTo(COUPONS_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.failureCount()).isEqualTo(1);
		server.verify();
	}

	@Test
	void htmlInsteadOfCouponJsonIsReportedAsASessionProblem() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		server.expect(requestTo(COUPONS_URL)).andRespond(withSuccess("<html>challenge</html>", MediaType.TEXT_HTML));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.details()).singleElement()
			.extracting(CouponDetail::message)
			.asString()
			.contains("HTML instead of JSON");
	}

	@Test
	void aFailedDeactivationAbortsTheRunWithAReportedError() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		expectCoupons(catalogue(coupon("c1", "ACTIVE", "retail", "1%", "03")));
		server.expect(requestTo(DEACTIVATE_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).singleElement().extracting(CouponDetail::message).asString().contains("403");
		server.verify();
	}

	@Test
	void aFailedActivationIsReportedInsteadOfThrowing() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("c1", "OPEN", "retail", "1%", "03"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);
		server.expect(requestTo(ACTIVATE_URL))
			.andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.failureCount()).isEqualTo(1);
		assertThat(result.details()).singleElement().extracting(CouponDetail::message).asString().contains("429");
		server.verify();
	}

	@Test
	void htmlOnActivationIsReportedAsASessionProblem() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("c1", "OPEN", "retail", "1%", "03"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);
		server.expect(requestTo(ACTIVATE_URL)).andRespond(withSuccess("<html>nope</html>", MediaType.TEXT_HTML));

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.details()).singleElement()
			.extracting(CouponDetail::message)
			.asString()
			.contains("session may have expired");
		server.verify();
	}

	@Test
	void nothingEligibleMeansNoActivationCall() {
		givenFilter(20, "retail", "", List.of("03"));
		expectJwt();
		String catalogue = catalogue(coupon("c1", "OPEN", "online", "1%", "03"));
		expectCoupons(catalogue);
		expectCoupons(catalogue);
		expectCoupons(catalogue);

		CouponActivationResult result = supercardCouponService.activateAllAvailableCoupons(COOKIES, "ua", "de");

		assertThat(result.successCount()).isZero();
		server.verify();
	}

}
