package com.patbaumgartner.couponbooster.migros.model;

import java.util.List;

/**
 * Immutable summary of a coupon-activation batch.
 */
public record CouponActivationResult(int successCount, int failureCount, List<CouponDetail> details) {

	public CouponActivationResult(int successCount, int failureCount, List<CouponDetail> details) {
		this.successCount = successCount;
		this.failureCount = failureCount;
		this.details = details == null ? List.of() : List.copyOf(details);
	}

}
