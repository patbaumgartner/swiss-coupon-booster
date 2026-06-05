package com.patbaumgartner.couponbooster.model;

import com.microsoft.playwright.options.Cookie;

import java.time.Instant;
import java.util.List;

/**
 * Immutable value object that carries the outcome of an authentication attempt.
 * <p>
 * Use the static factory methods {@link #successful} and {@link #failed} rather than
 * calling the canonical constructor directly.
 */
public record AuthenticationResult(boolean isSuccessful, String statusMessage, List<Cookie> sessionCookies,
		Instant completionTimestamp, long executionDurationMs, String userAgent, String browserLanguage) {

	public AuthenticationResult {
		sessionCookies = sessionCookies == null ? List.of() : List.copyOf(sessionCookies);
	}

	/**
	 * Creates a successful authentication result.
	 * @param retrievedCookies the session cookies obtained after login
	 * @param executionTimeMs elapsed time in milliseconds
	 * @param userAgent the browser user-agent string
	 * @param browserLanguage the browser language (e.g. {@code de-CH})
	 * @return a new successful {@code AuthenticationResult}
	 */
	public static AuthenticationResult successful(List<Cookie> retrievedCookies, long executionTimeMs, String userAgent,
			String browserLanguage) {
		return new AuthenticationResult(true, "Authentication completed successfully", retrievedCookies, Instant.now(),
				executionTimeMs, userAgent, browserLanguage);
	}

	/**
	 * Creates a failed authentication result.
	 * @param errorMessage human-readable description of why authentication failed
	 * @param executionTimeMs elapsed time in milliseconds
	 * @return a new failed {@code AuthenticationResult} with an empty cookie list
	 */
	public static AuthenticationResult failed(String errorMessage, long executionTimeMs) {
		return new AuthenticationResult(false, errorMessage, List.of(), Instant.now(), executionTimeMs, null, null);
	}

}
