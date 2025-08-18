package com.patbaumgartner.migroscouponbooster.config;

public interface ApplicationConstants {

	interface HttpHeaders {

		String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

		String USER_AGENT_HEADER = "User-Agent";

		String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

		String CONNECTION_HEADER = "Connection";

	}

	interface CookieNames {

		String CSRF_COOKIE_NAME = "CSRF";

		String AUTHENTICATION_DOMAIN = "account.migros.ch";

	}

	interface ApplicationInfo {

		String APPLICATION_NAME = "Coupon-Booster";

		String APPLICATION_VERSION = "1.0.0";

		String FRAMEWORK_VERSION = "Spring-Boot/3.5.4";

		String DEFAULT_USER_AGENT = APPLICATION_NAME + "/" + APPLICATION_VERSION + " " + FRAMEWORK_VERSION;

	}

}
