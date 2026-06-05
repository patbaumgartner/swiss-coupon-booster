package com.patbaumgartner.couponbooster.migros.model;

/**
 * Outcome of a single coupon activation attempt.
 */
public record CouponDetail(String couponName, String couponId, boolean success, String message) {
}
