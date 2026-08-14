package com.patbaumgartner.couponbooster.service;

import com.patbaumgartner.couponbooster.model.SessionCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract base class for coupon services.
 * <p>
 * Provides common utility methods for handling cookies and logging activation summaries.
 */
public abstract class AbstractCouponService implements CouponService {

	private static final Logger log = LoggerFactory.getLogger(AbstractCouponService.class);

	/**
	 * Builds a semicolon-separated cookie header string from a list of cookies.
	 * @param sessionCookies the list of cookies
	 * @return the cookie header string
	 */
	protected String buildCookieHeader(final List<SessionCookie> sessionCookies) {
		return sessionCookies.stream()
			.map(cookie -> cookie.name() + "=" + cookie.value())
			.collect(Collectors.joining("; "));
	}

	/**
	 * Retains only the cookies that RFC 6265 permits to be sent to {@code targetHost}.
	 * <p>
	 * Both providers are authenticated in the same sidecar, so a single response can
	 * carry cookies for either retailer. Matching on the host keeps one retailer's
	 * session out of the other retailer's API requests.
	 * @param allCookies every cookie returned by the sidecar
	 * @param targetHost the host the request will be sent to, e.g.
	 * {@code www.supercard.ch}
	 * @return the cookies belonging to {@code targetHost}
	 */
	protected List<SessionCookie> filterDomainSpecificCookies(final List<SessionCookie> allCookies,
			final String targetHost) {
		return allCookies.stream().filter(cookie -> cookie.matchesHost(targetHost)).toList();
	}

	/**
	 * Logs a summary of the coupon activation process.
	 * @param successCount the number of successfully activated coupons
	 * @param failureCount the number of failed activations
	 * @param totalAttempts the total number of activation attempts
	 */
	protected void logActivationSummary(int successCount, int failureCount, int totalAttempts) {
		if (successCount > 0 && totalAttempts > 0) {
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

}
