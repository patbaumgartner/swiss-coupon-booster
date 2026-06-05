package com.patbaumgartner.couponbooster.migros.scheduler;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.scheduler.AbstractCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
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
public class MigrosCouponBoosterScheduler extends AbstractCouponBoosterScheduler {

	/**
	 * Constructs a new {@code MigrosCouponBoosterScheduler} with the specified services.
	 * @param migrosAuthenticationService the authentication service to use (sidecar or
	 * browser mode)
	 * @param cumulusCouponService the service to use for coupon activation
	 */
	public MigrosCouponBoosterScheduler(@Qualifier("migrosAuth") AuthenticationService migrosAuthenticationService,
			CumulusCouponService cumulusCouponService) {
		super(migrosAuthenticationService, cumulusCouponService, "Migros");
	}

	/**
	 * Triggers the daily coupon activation flow.
	 * <p>
	 * Called by the Spring scheduler according to {@code migros.scheduler.cron}.
	 */
	@Scheduled(cron = "${migros.scheduler.cron}", zone = "${couponbooster.scheduler.zone:Europe/Zurich}")
	public void runDailyActivation() {
		runActivation();
	}

}
