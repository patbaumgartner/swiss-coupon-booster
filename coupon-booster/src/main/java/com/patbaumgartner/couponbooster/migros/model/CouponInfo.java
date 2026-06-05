package com.patbaumgartner.couponbooster.migros.model;

public record CouponInfo(String id, String name, String description, String validUntil, boolean activated) {
}
