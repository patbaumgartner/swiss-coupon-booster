package com.patbaumgartner.couponbooster.migros.runner;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.migros.service.MigrosAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ApplicationRunner} for Migros Cumulus coupon activation.
 * <p>
 * This runner orchestrates the authentication and coupon activation process for Migros
 * Cumulus. It is conditionally enabled based on the {@code migros.login.enabled}
 * property.
 *
 * @see com.patbaumgartner.couponbooster.migros.service.MigrosAuthenticationService
 * @see com.patbaumgartner.couponbooster.migros.service.CumulusCouponService
 */
@Component
@ConditionalOnProperty(value = "migros.login.enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrosCouponBoosterRunner.class);

	private final MigrosAuthenticationService migrosAuthenticationService;

	private final CumulusCouponService cumulusCouponService;

	/**
	 * Constructs a new {@code MigrosCouponBoosterRunner} with the specified services.
	 * @param migrosAuthenticationService the service to use for authentication
	 * @param couponActivationService the service to use for coupon activation
	 */
	public MigrosCouponBoosterRunner(MigrosAuthenticationService migrosAuthenticationService,
			CumulusCouponService couponActivationService) {
		this.migrosAuthenticationService = migrosAuthenticationService;
		this.cumulusCouponService = couponActivationService;
	}

	/**
	 * Executes the Migros coupon activation process.
	 * <p>
	 * This method first performs authentication and, if successful, proceeds to activate
	 * all available Cumulus coupons.
	 * @param applicationArgs the application arguments
	 * @throws Exception if an error occurs during the process
	 */
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
