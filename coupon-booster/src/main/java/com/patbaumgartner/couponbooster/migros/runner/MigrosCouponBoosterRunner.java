package com.patbaumgartner.couponbooster.migros.runner;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@link ApplicationRunner} for Migros Cumulus coupon activation.
 * <p>
 * This runner orchestrates the authentication and coupon activation process for Migros
 * Cumulus. It is conditionally enabled based on the {@code migros.startup-run.enabled}
 * property.
 *
 * @see com.patbaumgartner.couponbooster.migros.service.MigrosAuthenticationService
 * @see com.patbaumgartner.couponbooster.migros.service.CumulusCouponService
 */
@Component
@ConditionalOnProperty(value = "migros.startup-run.enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCouponBoosterRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrosCouponBoosterRunner.class);

	private final AuthenticationService migrosAuthenticationService;

	private final CumulusCouponService cumulusCouponService;

	/**
	 * Constructs a new {@code MigrosCouponBoosterRunner} with the specified services.
	 * @param migrosAuthenticationService the service to use for authentication
	 * @param cumulusCouponService the service to use for coupon activation
	 */
	public MigrosCouponBoosterRunner(@Qualifier("migrosAuth") AuthenticationService migrosAuthenticationService,
			CumulusCouponService cumulusCouponService) {
		this.migrosAuthenticationService = Objects.requireNonNull(migrosAuthenticationService,
				"MigrosAuthenticationService cannot be null");
		this.cumulusCouponService = Objects.requireNonNull(cumulusCouponService, "CumulusCouponService cannot be null");
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
				log.info("Authentication successful - {} cookies in {}ms", authenticationResult.sessionCookies().size(),
						authenticationResult.executionDurationMs());
			}

			var activationResult = cumulusCouponService.activateAllAvailableCoupons(
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
