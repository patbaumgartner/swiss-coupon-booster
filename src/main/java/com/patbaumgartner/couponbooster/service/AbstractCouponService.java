package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.options.Cookie;
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
	protected String buildCookieHeader(final List<Cookie> sessionCookies) {
		return sessionCookies.stream()
			.map(cookie -> cookie.name + "=" + cookie.value)
			.collect(Collectors.joining("; "));
	}

	/**
	 * Filters a list of cookies to retain only those relevant for the target domain.
	 * @param allCookies the list of all cookies
	 * @param targetDomain the target domain to filter by
	 * @return the filtered list of cookies
	 */
	protected List<Cookie> filterDomainSpecificCookies(final List<Cookie> allCookies, final String targetDomain) {
		return allCookies.stream()
			.filter(cookie -> cookie.domain.startsWith(".") || cookie.domain.startsWith(targetDomain))
			.toList();
	}

	/**
	 * Logs a summary of the coupon activation process.
	 * @param successCount the number of successfully activated coupons
	 * @param failureCount the number of failed activations
	 * @param totalAttempts the total number of activation attempts
	 */
	protected void logActivationSummary(int successCount, int failureCount, int totalAttempts) {
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

}
