package com.patbaumgartner.couponbooster.coop.scheduler;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.scheduler.AbstractCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
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
public class CoopCouponBoosterScheduler extends AbstractCouponBoosterScheduler {

	/**
	 * Constructs a new {@code CoopCouponBoosterScheduler} with the specified services.
	 * @param coopAuthenticationService the authentication service to use (sidecar or
	 * browser mode)
	 * @param supercardCouponService the service to use for coupon activation
	 */
	public CoopCouponBoosterScheduler(@Qualifier("coopAuth") AuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService) {
		super(coopAuthenticationService, supercardCouponService, "Coop");
	}

	/**
	 * Triggers the daily coupon activation flow.
	 * <p>
	 * Called by the Spring scheduler according to {@code coop.scheduler.cron}.
	 */
	@Scheduled(cron = "${coop.scheduler.cron}", zone = "${couponbooster.scheduler.zone:Europe/Zurich}")
	public void runDailyActivation() {
		runActivation();
	}

}
