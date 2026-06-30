package com.patbaumgartner.couponbooster.scheduler;

import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for coupon booster scheduled activation tasks.
 * <p>
 * Encapsulates the shared daily activation flow: authenticate, then activate all
 * available coupons. Subclasses supply the provider-specific
 * {@link AuthenticationService} and {@link CouponService} via the constructor, and
 * declare a {@code @Scheduled} method that calls {@link #runActivation()}.
 *
 * @see com.patbaumgartner.couponbooster.coop.scheduler.CoopCouponBoosterScheduler
 * @see com.patbaumgartner.couponbooster.migros.scheduler.MigrosCouponBoosterScheduler
 */
public abstract class AbstractCouponBoosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(AbstractCouponBoosterScheduler.class);

	private final AuthenticationService authenticationService;

	private final CouponService couponService;

	private final String providerName;

	/**
	 * Guards against overlapping runs (e.g. a manual trigger during a scheduled run).
	 */
	private final AtomicBoolean activationInProgress = new AtomicBoolean(false);

	/**
	 * Constructs a new coupon booster scheduler.
	 * @param authenticationService the authentication service for this provider
	 * @param couponService the coupon activation service for this provider
	 * @param providerName human-readable provider label used in log messages (e.g.
	 * {@code "Coop"} or {@code "Migros"})
	 */
	protected AbstractCouponBoosterScheduler(AuthenticationService authenticationService, CouponService couponService,
			String providerName) {
		this.authenticationService = Objects.requireNonNull(authenticationService,
				"AuthenticationService cannot be null");
		this.couponService = Objects.requireNonNull(couponService, "CouponService cannot be null");
		this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null");
	}

	/**
	 * Executes the coupon activation flow for the provider. Intended to be called from
	 * the subclass {@code @Scheduled} method as well as the manual REST trigger.
	 * <p>
	 * Runs are mutually exclusive: if an activation is already in progress for this
	 * provider, the call is ignored and an empty {@link Optional} is returned.
	 * @return the {@link ActivationOutcome} of the run, or {@link Optional#empty()} if a
	 * run was already in progress
	 */
	public Optional<ActivationOutcome> runActivation() {
		if (!activationInProgress.compareAndSet(false, true)) {
			log.warn("{} activation already in progress; ignoring new request", providerName);
			return Optional.empty();
		}
		try {
			return Optional.of(executeActivation());
		}
		finally {
			activationInProgress.set(false);
		}
	}

	private ActivationOutcome executeActivation() {
		log.info("Starting {} coupon activation", providerName);

		var authenticationResult = authenticationService.performAuthentication();

		if (!authenticationResult.isSuccessful()) {
			log.error("Authentication failed: {} ({}ms)", authenticationResult.statusMessage(),
					authenticationResult.executionDurationMs());
			return new ActivationOutcome(providerName, false, 0, 0, authenticationResult.executionDurationMs(),
					authenticationResult.statusMessage());
		}

		if (log.isInfoEnabled()) {
			log.info("Authentication successful - {} cookies in {}ms", authenticationResult.sessionCookies().size(),
					authenticationResult.executionDurationMs());
		}

		var activationResult = couponService.activateAllAvailableCoupons(authenticationResult.sessionCookies(),
				authenticationResult.userAgent(), authenticationResult.browserLanguage());

		if (log.isInfoEnabled()) {
			log.info("Completed - {} activated, {} failed", activationResult.successCount(),
					activationResult.failureCount());
		}

		return new ActivationOutcome(providerName, true, activationResult.successCount(),
				activationResult.failureCount(), authenticationResult.executionDurationMs(), "Activation completed");
	}

}
