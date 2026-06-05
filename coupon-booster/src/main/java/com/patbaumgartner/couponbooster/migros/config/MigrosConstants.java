package com.patbaumgartner.couponbooster.migros.config;

/**
 * Constants for Migros Cumulus integration including HTTP headers and cookie names used
 * during authentication and API communication.
 */
public interface MigrosConstants {

	/**
	 * HTTP headers used in Migros Cumulus API requests.
	 */
	interface HttpHeaders {

		/**
		 * The CSRF token header name required for state-changing requests.
		 */
		String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

	}

	/**
	 * Cookie names used in Migros Cumulus authentication flow.
	 */
	interface Cookies {

		/**
		 * CSRF cookie name from which the token value is extracted.
		 */
		String CSRF_COOKIE_NAME = "CSRF";

		/**
		 * Domain for authentication cookies.
		 */
		String AUTHENTICATION_DOMAIN = "account.migros.ch";

	}

}
