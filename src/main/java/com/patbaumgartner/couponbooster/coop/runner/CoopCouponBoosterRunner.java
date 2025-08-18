package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.CoopAuthenticationService;
import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ApplicationRunner} for Coop Supercard coupon activation.
 * <p>
 * This runner is responsible for orchestrating the authentication and coupon activation
 * process for Coop Supercard. It is conditionally enabled based on the
 * {@code coop.login.enabled} property.
 *
 * @see com.patbaumgartner.couponbooster.coop.service.CoopAuthenticationService
 * @see com.patbaumgartner.couponbooster.coop.service.SupercardCouponService
 */
@Component
@ConditionalOnProperty(value = "coop.login.enabled", havingValue = "true", matchIfMissing = true)
public class CoopCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CoopCouponBoosterRunner.class);

	private final CoopAuthenticationService coopAuthenticationService;

	private final SupercardCouponService supercardCouponService;

	/**
	 * Constructs a new {@code CoopCouponBoosterRunner} with the specified services.
	 * @param coopAuthenticationService the service to use for authentication
	 * @param supercardCouponService the service to use for coupon activation
	 */
	public CoopCouponBoosterRunner(CoopAuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService) {
		this.coopAuthenticationService = coopAuthenticationService;
		this.supercardCouponService = supercardCouponService;
	}

	/**
	 * Executes the Coop coupon activation process.
	 * <p>
	 * This method first performs authentication and, if successful, proceeds to activate
	 * all available Supercard coupons.
	 * @param applicationArgs the application arguments
	 * @throws Exception if an error occurs during the process
	 */
	@Override
	public void run(ApplicationArguments applicationArgs) throws Exception {
		log.info("Starting Coop coupon booster runner");

		var authenticationResult = coopAuthenticationService.performAuthentication();

		if (authenticationResult.isSuccessful()) {
			if (log.isInfoEnabled()) {
				log.info("Authentication successful - {} cookies in {}ms", authenticationResult.sessionCookies().size(),
						authenticationResult.executionDurationMs());
			}

			var activationResult = supercardCouponService.activateAllAvailableCoupons(
					authenticationResult.sessionCookies(), authenticationResult.userAgent(),
					authenticationResult.browserLanguage());

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
