package com.patbaumgartner.couponbooster.migros.config;

public interface MigrosConstants {

	interface HttpHeaders {

		String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

		String USER_AGENT_HEADER = "User-Agent";

		String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

		String CONNECTION_HEADER = "Connection";

	}

	interface CookieNames {

		String CSRF_COOKIE = "CSRF";

		String AUTHENTICATION_DOMAIN = "account.migros.ch";

	}

}
