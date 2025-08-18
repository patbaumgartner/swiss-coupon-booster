package com.patbaumgartner.couponbooster.migros.runner;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "migros.login.enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrosCouponBoosterRunner.class);

	private final AuthenticationService migrosAuthenticationService;

	private final CumulusCouponService cumulusCouponService;

	public MigrosCouponBoosterRunner(AuthenticationService migrosAuthenticationService,
			CumulusCouponService couponActivationService) {
		this.migrosAuthenticationService = migrosAuthenticationService;
		this.cumulusCouponService = couponActivationService;
	}

	@Override
	public void run(ApplicationArguments applicationArgs) throws Exception {
		log.info("Starting Migros coupon booster runner");

		var authenticationResult = migrosAuthenticationService.performAuthentication();

		if (authenticationResult.isSuccessful()) {
			if (log.isInfoEnabled()) {
				log.info("Authentication successful - captured {} session cookies in {}ms",
						authenticationResult.sessionCookies().size(), authenticationResult.executionDurationMs());
			}

			var activationResult = cumulusCouponService.activateAllAvailableCoupons(
					authenticationResult.sessionCookies(), authenticationResult.userAgent(),
					authenticationResult.browserLanguage());

			if (log.isInfoEnabled()) {
				log.info("Runner completed - {} coupons activated successfully, {} failed",
						activationResult.successCount(), activationResult.failureCount());
			}
		}
		else {
			log.error("Authentication failed: {} (took {}ms)", authenticationResult.statusMessage(),
					authenticationResult.executionDurationMs());
		}
	}

}
