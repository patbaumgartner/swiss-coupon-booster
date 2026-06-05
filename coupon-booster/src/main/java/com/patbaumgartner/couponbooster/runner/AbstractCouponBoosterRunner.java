package com.patbaumgartner.couponbooster.runner;

import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

/**
 * Abstract base class for coupon booster application runners.
 * <p>
 * Encapsulates the shared startup activation flow: authenticate, then activate all
 * available coupons. Subclasses supply the provider-specific
 * {@link AuthenticationService} and {@link CouponService} via the constructor.
 *
 * @see com.patbaumgartner.couponbooster.coop.runner.CoopCouponBoosterRunner
 * @see com.patbaumgartner.couponbooster.migros.runner.MigrosCouponBoosterRunner
 */
public abstract class AbstractCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AbstractCouponBoosterRunner.class);

	private final AuthenticationService authenticationService;

	private final CouponService couponService;

	private final String providerName;

	/**
	 * Constructs a new coupon booster runner.
	 * @param authenticationService the authentication service for this provider
	 * @param couponService the coupon activation service for this provider
	 * @param providerName human-readable provider label used in log messages (e.g.
	 * {@code "Coop"} or {@code "Migros"})
	 */
	protected AbstractCouponBoosterRunner(AuthenticationService authenticationService, CouponService couponService,
			String providerName) {
		this.authenticationService = Objects.requireNonNull(authenticationService,
				"AuthenticationService cannot be null");
		this.couponService = Objects.requireNonNull(couponService, "CouponService cannot be null");
		this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null");
	}

	/**
	 * Executes the coupon activation process for the provider.
	 * @param applicationArgs the application arguments
	 * @throws Exception if an error occurs during the process
	 */
	@Override
	public void run(ApplicationArguments applicationArgs) throws Exception {
		log.info("Starting {} coupon booster runner", providerName);

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
