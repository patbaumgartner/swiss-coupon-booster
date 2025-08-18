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

		/** The X-Client-Id header name used to identify the web application. */
		String X_CLIENT_ID = "X-Client-Id";

		/** The X-Client-Id header value for web SuperCard application. */
		String X_CLIENT_ID_VALUE = "WEB_SUPERCARD";

	}

	/**
	 * Cookie names used in Coop SuperCard authentication flow.
	 */
	interface CookieNames {

		/** CMS session ID cookie for maintaining session state. */
		String CMSSESSIONID_COOKIE = "CMSSESSIONID";

		/** DataDome cookie for bot protection bypass. */
		String DATADOME_COOKIE = "datadome";

		/** Domain for authentication cookies. */
		String AUTHENTICATION_DOMAIN = "www.supercard.ch";

	}

}
