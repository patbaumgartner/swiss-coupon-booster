package com.patbaumgartner.swiss.coupon.booster.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiss.coupon.booster")
public record SwissCouponBoosterSettings(boolean headlessEnabled, boolean debugCookies) {
}
