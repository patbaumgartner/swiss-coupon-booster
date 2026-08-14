package com.patbaumgartner.couponbooster.service;

import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.model.SessionCookie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AbstractCouponServiceTest {

	private final TestCouponService service = new TestCouponService();

	@Test
	void buildCookieHeader_joinsPairsWithSemicolons() {
		var header = service
			.cookieHeader(List.of(new SessionCookie("a", "1", ".x.ch"), new SessionCookie("b", "2", ".x.ch")));

		assertThat(header).isEqualTo("a=1; b=2");
	}

	@Test
	void buildCookieHeader_returnsEmptyStringForNoCookies() {
		assertThat(service.cookieHeader(List.of())).isEmpty();
	}

	@Test
	void filterDomainSpecificCookies_keepsOnlyCookiesForTheTargetHost() {
		var cookies = List.of(new SessionCookie("supercard", "1", ".supercard.ch"),
				new SessionCookie("migros", "2", ".migros.ch"), new SessionCookie("hostOnly", "3", "www.supercard.ch"),
				new SessionCookie("other", "4", "webapi.supercard.ch"));

		var filtered = service.filter(cookies, "www.supercard.ch");

		assertThat(filtered).extracting(SessionCookie::name).containsExactly("supercard", "hostOnly");
	}

	@Test
	void logActivationSummary_doesNotDivideByZeroWhenNothingWasAttempted() {
		assertThatCode(() -> service.summary(0, 0, 0)).doesNotThrowAnyException();
		assertThatCode(() -> service.summary(1, 0, 0)).doesNotThrowAnyException();
	}

	private static final class TestCouponService extends AbstractCouponService {

		@Override
		public CouponActivationResult activateAllAvailableCoupons(List<SessionCookie> sessionCookies, String userAgent,
				String language) {
			return new CouponActivationResult(0, 0, List.of());
		}

		String cookieHeader(List<SessionCookie> cookies) {
			return buildCookieHeader(cookies);
		}

		List<SessionCookie> filter(List<SessionCookie> cookies, String host) {
			return filterDomainSpecificCookies(cookies, host);
		}

		void summary(int success, int failure, int total) {
			logActivationSummary(success, failure, total);
		}

	}

}
