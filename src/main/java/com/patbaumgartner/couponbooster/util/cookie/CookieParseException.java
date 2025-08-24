package com.patbaumgartner.couponbooster.util.cookie;

/**
 * Runtime exception for parse errors.
 */
public final class CookieParseException extends RuntimeException {

	public CookieParseException(String message) {
		super(message);
	}

	public CookieParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
