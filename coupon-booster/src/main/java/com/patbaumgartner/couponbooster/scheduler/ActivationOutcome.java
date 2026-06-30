package com.patbaumgartner.couponbooster.scheduler;

/**
 * Immutable summary of a single coupon-activation run, returned by
 * {@link AbstractCouponBoosterScheduler#runActivation()} so callers (the scheduler and
 * the manual REST trigger) can report the result.
 *
 * @param provider human-readable provider label (e.g. {@code "Coop"} or {@code "Migros"})
 * @param authenticated whether authentication succeeded
 * @param activated number of coupons successfully activated
 * @param failed number of coupons that failed to activate
 * @param authDurationMs authentication duration in milliseconds
 * @param message human-readable status message
 */
public record ActivationOutcome(String provider, boolean authenticated, int activated, int failed, long authDurationMs,
		String message) {
}
