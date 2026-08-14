package com.patbaumgartner.couponbooster.migros.runner;

import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.runner.AbstractCouponBoosterRunner;
import com.patbaumgartner.couponbooster.runner.ActivationExitCode;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ApplicationRunner} for Migros Cumulus coupon activation.
 * <p>
 * Conditionally enabled based on the {@code migros.startup-run.enabled} property.
 *
 * @see com.patbaumgartner.couponbooster.migros.service.MigrosSidecarAuthenticationService
 * @see com.patbaumgartner.couponbooster.migros.service.CumulusCouponService
 */
@Component
@ConditionalOnProperty(value = "migros.startup-run.enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCouponBoosterRunner extends AbstractCouponBoosterRunner {

	/**
	 * Constructs a new {@code MigrosCouponBoosterRunner}.
	 * @param migrosAuthenticationService the authentication service to use
	 * @param cumulusCouponService the coupon activation service
	 * @param exitCode collects run outcomes for the process exit code
	 */
	public MigrosCouponBoosterRunner(@Qualifier("migrosAuth") AuthenticationService migrosAuthenticationService,
			CumulusCouponService cumulusCouponService, ActivationExitCode exitCode) {
		super(migrosAuthenticationService, cumulusCouponService, "Migros", exitCode);
	}

}
