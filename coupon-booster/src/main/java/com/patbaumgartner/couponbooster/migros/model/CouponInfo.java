package com.patbaumgartner.couponbooster.migros.model;

/**
 * Coupon metadata returned by the Cumulus API.
 */
public record CouponInfo(String id, String name, String description, String validUntil, boolean activated) {
}
