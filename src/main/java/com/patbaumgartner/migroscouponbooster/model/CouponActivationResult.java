package com.patbaumgartner.migroscouponbooster.model;

import java.util.List;

public record CouponActivationResult(int successCount, int failureCount, List<CouponDetail> details) {

	public CouponActivationResult(int successCount, int failureCount, List<CouponDetail> details) {
		this.successCount = successCount;
		this.failureCount = failureCount;
		this.details = details == null ? List.of() : List.copyOf(details);
	}

	public List<CouponDetail> details() {
		return details; // Already immutable from constructor
	}
}
