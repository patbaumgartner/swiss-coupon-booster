package com.patbaumgartner.couponbooster.coop.config;

/**
 * Constants for Coop SuperCard integration including HTTP headers and cookie names used
 * during authentication and API communication.
 */
public interface CoopConstants {

	/**
	 * HTTP headers used in Coop SuperCard API requests.
	 */
	interface HttpHeaders {

		/**
		 * The X-Client-Id header name used to identify the web application.
		 */
		String X_CLIENT_ID = "X-Client-Id";

		/**
		 * The X-Client-Id header value for web SuperCard application.
		 */
		String X_CLIENT_ID_VALUE = "WEB_SUPERCARD";

	}

	/**
	 * Cookie names used in Coop SuperCard authentication flow.
	 */
	interface CookieNames {

		/**
		 * DataDome cookie for bot protection bypass.
		 */
		String DATADOME_COOKIE = "datadome";

		/**
		 * Wildcard Domain for authentication cookies.
		 */
		String WILDCARD_COOKIE_DOMAIN = ".supercard.ch";

		/**
		 * Domain for authentication cookies.
		 */
		String AUTHENTICATION_DOMAIN = "www.supercard.ch";

	}

	/**
	 * Timeouts and delays (in milliseconds) used for bot evasion and UI interactions.
	 */
	interface Delays {

		/** Min delay before interacting with cookie consent. */
		int COOKIE_CONSENT_MIN = 500;

		/** Max delay before interacting with cookie consent. */
		int COOKIE_CONSENT_MAX = 1500;

		/** Min delay before clicking login link. */
		int LOGIN_CLICK_MIN = 300;

		/** Max delay before clicking login link. */
		int LOGIN_CLICK_MAX = 800;

		/** Min delay before typing username/password. */
		int INPUT_TYPING_MIN = 400; // slightly generalized

		/** Max delay before typing username/password. */
		int INPUT_TYPING_MAX = 1200;

		/** Min delay before clicking submit. */
		int SUBMIT_CLICK_MIN = 300;

		/** Max delay before clicking submit. */
		int SUBMIT_CLICK_MAX = 700;

		/** Min delay to wait for DataDome initial checks. */
		int DATADOME_CHECK_MIN = 3000;

		/** Max delay to wait for DataDome initial checks. */
		int DATADOME_CHECK_MAX = 5000;

	}

}
