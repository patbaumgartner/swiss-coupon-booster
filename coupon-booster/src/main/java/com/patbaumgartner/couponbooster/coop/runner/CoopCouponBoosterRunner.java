package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.runner.AbstractCouponBoosterRunner;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ApplicationRunner} for Coop Supercard coupon activation.
 * <p>
 * Conditionally enabled based on the {@code coop.startup-run.enabled} property.
 * Authentication is delegated to whichever {@link AuthenticationService} bean is active
 * (sidecar or browser mode, controlled by {@code coop.auth.mode}).
 *
 * @see com.patbaumgartner.couponbooster.coop.service.CoopStealthAuthenticationService
 * @see com.patbaumgartner.couponbooster.coop.service.CoopAuthenticationService
 * @see com.patbaumgartner.couponbooster.coop.service.SupercardCouponService
 */
@Component
@ConditionalOnProperty(value = "coop.startup-run.enabled", havingValue = "true", matchIfMissing = true)
public class CoopCouponBoosterRunner extends AbstractCouponBoosterRunner {

	/**
	 * Constructs a new {@code CoopCouponBoosterRunner}.
	 * @param coopAuthenticationService the authentication service to use
	 * @param supercardCouponService the coupon activation service
	 */
	public CoopCouponBoosterRunner(@Qualifier("coopAuth") AuthenticationService coopAuthenticationService,
			SupercardCouponService supercardCouponService) {
		super(coopAuthenticationService, supercardCouponService, "Coop");
	}

}
