package com.patbaumgartner.couponbooster.scheduler;

import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Abstract base class for coupon booster scheduled activation tasks.
 * <p>
 * Encapsulates the shared daily activation flow: authenticate, then activate all
 * available coupons. Subclasses supply the provider-specific
 * {@link AuthenticationService} and {@link CouponService} via the constructor, and
 * declare a {@code @Scheduled} method that calls {@link #runActivation()}.
 *
 * @see com.patbaumgartner.couponbooster.coop.scheduler.CoopCouponBoosterScheduler
 * @see com.patbaumgartner.couponbooster.migros.scheduler.MigrosCouponBoosterScheduler
 */
public abstract class AbstractCouponBoosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(AbstractCouponBoosterScheduler.class);

	private final AuthenticationService authenticationService;

	private final CouponService couponService;

	private final String providerName;

	/**
	 * Constructs a new coupon booster scheduler.
	 * @param authenticationService the authentication service for this provider
	 * @param couponService the coupon activation service for this provider
	 * @param providerName human-readable provider label used in log messages (e.g.
	 * {@code "Coop"} or {@code "Migros"})
	 */
	protected AbstractCouponBoosterScheduler(AuthenticationService authenticationService, CouponService couponService,
			String providerName) {
		this.authenticationService = Objects.requireNonNull(authenticationService,
				"AuthenticationService cannot be null");
		this.couponService = Objects.requireNonNull(couponService, "CouponService cannot be null");
		this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null");
	}

	/**
	 * Executes the coupon activation flow for the provider. Intended to be called from
	 * the subclass {@code @Scheduled} method.
	 */
	protected void runActivation() {
		log.info("Starting scheduled {} coupon activation", providerName);

		var authenticationResult = authenticationService.performAuthentication();

		if (authenticationResult.isSuccessful()) {
			if (log.isInfoEnabled()) {
				log.info("Authentication successful - {} cookies in {}ms", authenticationResult.sessionCookies().size(),
						authenticationResult.executionDurationMs());
			}

			var activationResult = couponService.activateAllAvailableCoupons(authenticationResult.sessionCookies(),
					authenticationResult.userAgent(), authenticationResult.browserLanguage());

			if (log.isInfoEnabled()) {
				log.info("Completed - {} activated, {} failed", activationResult.successCount(),
						activationResult.failureCount());
			}
		}
		else {
			log.error("Authentication failed: {} ({}ms)", authenticationResult.statusMessage(),
					authenticationResult.executionDurationMs());
		}
	}

}
