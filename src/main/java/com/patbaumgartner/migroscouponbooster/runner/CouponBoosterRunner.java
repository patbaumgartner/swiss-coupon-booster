package com.patbaumgartner.migroscouponbooster.runner;

import com.patbaumgartner.migroscouponbooster.model.AuthenticationResult;
import com.patbaumgartner.migroscouponbooster.service.CumulusCouponService;
import com.patbaumgartner.migroscouponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "migros.login.enabled", havingValue = "true", matchIfMissing = true)
public class CouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CouponBoosterRunner.class);

	private final AuthenticationService authenticationService;

	private final CumulusCouponService couponActivationService;

	public CouponBoosterRunner(AuthenticationService authenticationService,
			CumulusCouponService couponActivationService) {
		this.authenticationService = authenticationService;
		this.couponActivationService = couponActivationService;
	}

	@Override
	public void run(ApplicationArguments applicationArgs) throws Exception {
		log.info("Starting coupon booster application");

		var authenticationResult = authenticationService.performAuthentication();

		if (authenticationResult.isSuccessful()) {
			if (log.isInfoEnabled()) {
				log.info("Authentication successful - captured {} session cookies in {}ms",
						authenticationResult.sessionCookies().size(), authenticationResult.executionDurationMs());
			}

			var activationResult = couponActivationService
				.activateAllAvailableCoupons(authenticationResult.sessionCookies());

			if (log.isInfoEnabled()) {
				log.info("Application completed - {} coupons activated successfully, {} failed",
						activationResult.successCount(), activationResult.failureCount());
			}
		}
		else {
			log.error("Authentication failed: {} (took {}ms)", authenticationResult.statusMessage(),
					authenticationResult.executionDurationMs());
		}
	}

}
