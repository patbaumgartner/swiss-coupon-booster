package com.patbaumgartner.couponbooster.coop.scheduler;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily Coop coupon activation for the long-running server profile.
 */
@Component
@Profile("server")
@ConditionalOnProperty(value = "coop.scheduler.enabled", havingValue = "true")
public class CoopCouponBoosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(CoopCouponBoosterScheduler.class);

	private final AuthenticationService coopAuthenticationService;

	private final SupercardCouponService supercardCouponService;

	public CoopCouponBoosterScheduler(@Qualifier("coopAuth") AuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService) {
		this.coopAuthenticationService = Objects.requireNonNull(coopAuthenticationService,
				"CoopAuthenticationService cannot be null");
		this.supercardCouponService = Objects.requireNonNull(supercardCouponService,
				"SupercardCouponService cannot be null");
	}

	@Scheduled(cron = "${coop.scheduler.cron}", zone = "${couponbooster.scheduler.zone:Europe/Zurich}")
	public void runDailyActivation() {
		log.info("Starting scheduled Coop coupon activation");

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
