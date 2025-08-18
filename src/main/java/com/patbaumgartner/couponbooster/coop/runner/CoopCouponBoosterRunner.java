package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Coop SuperCard coupon activation runner. Performs authentication and coupon management
 * when enabled.
 */
@Component
@ConditionalOnProperty(value = "coop.login.enabled", havingValue = "true", matchIfMissing = true)
public class CoopCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CoopCouponBoosterRunner.class);

	private final AuthenticationService coopAuthenticationService;

	private final SupercardCouponService supercardCouponService;

	/**
	 * Creates runner with required services.
	 */
	public CoopCouponBoosterRunner(AuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService) {
		this.coopAuthenticationService = coopAuthenticationService;
		this.supercardCouponService = supercardCouponService;
	}

	/**
	 * Runs coupon activation: authenticate then activate coupons.
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
