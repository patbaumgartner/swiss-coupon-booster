package com.patbaumgartner.swiss.coupon.booster.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coop.supercard")
public record CoopSupercardSettings(String startUrl, String username, String password, String loginUrl,
		String datadomeCookie, String digitalBonsUrl, String configUrl, String configUrlReferer, String cmsCookie,
		String couponsUrl, String couponsDeactivationUrl, String couponsActivationUrl, boolean enabled) {
}
