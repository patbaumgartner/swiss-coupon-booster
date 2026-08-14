package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.runner.AbstractCouponBoosterRunner;
import com.patbaumgartner.couponbooster.runner.ActivationExitCode;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ApplicationRunner} for Coop Supercard coupon activation.
 * <p>
 * Conditionally enabled based on the {@code coop.startup-run.enabled} property.
 * Authentication is delegated to the {@link AuthenticationService} qualified as
 * {@code coopAuth}.
 *
 * @see com.patbaumgartner.couponbooster.coop.service.CoopSidecarAuthenticationService
 * @see com.patbaumgartner.couponbooster.coop.service.SupercardCouponService
 */
@Component
@ConditionalOnProperty(value = "coop.startup-run.enabled", havingValue = "true", matchIfMissing = true)
public class CoopCouponBoosterRunner extends AbstractCouponBoosterRunner {

	/**
	 * Constructs a new {@code CoopCouponBoosterRunner}.
	 * @param coopAuthenticationService the authentication service to use
	 * @param supercardCouponService the coupon activation service
	 * @param exitCode collects run outcomes for the process exit code
	 */
	public CoopCouponBoosterRunner(@Qualifier("coopAuth") AuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService, ActivationExitCode exitCode) {
		super(coopAuthenticationService, supercardCouponService, "Coop", exitCode);
	}

}
