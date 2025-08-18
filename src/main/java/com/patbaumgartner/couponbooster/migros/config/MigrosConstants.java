package com.patbaumgartner.couponbooster.migros.config;

public interface MigrosConstants {

	interface HttpHeaders {

		String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

	}

	interface Cookies {

		String CSRF_COOKIE_NAME = "CSRF";

		String AUTHENTICATION_DOMAIN = "account.migros.ch";

	}

}
