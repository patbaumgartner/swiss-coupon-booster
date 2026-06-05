package com.patbaumgartner.couponbooster.migros.scheduler;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily Migros coupon activation for the long-running server profile.
 */
@Component
@Profile("server")
@ConditionalOnProperty(value = "migros.scheduler.enabled", havingValue = "true")
public class MigrosCouponBoosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(MigrosCouponBoosterScheduler.class);

	private final AuthenticationService migrosAuthenticationService;

	private final CumulusCouponService cumulusCouponService;

	public MigrosCouponBoosterScheduler(@Qualifier("migrosAuth") AuthenticationService migrosAuthenticationService,
			CumulusCouponService cumulusCouponService) {
		this.migrosAuthenticationService = migrosAuthenticationService;
		this.cumulusCouponService = cumulusCouponService;
	}

	@Scheduled(cron = "${migros.scheduler.cron}", zone = "${couponbooster.scheduler.zone:Europe/Zurich}")
	public void runDailyActivation() {
		log.info("Starting scheduled Migros coupon activation");

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
