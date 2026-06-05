package com.patbaumgartner.couponbooster.model;

import com.microsoft.playwright.options.Cookie;

import java.time.Instant;
import java.util.List;

public record AuthenticationResult(boolean isSuccessful, String statusMessage, List<Cookie> sessionCookies,
		Instant completionTimestamp, long executionDurationMs, String userAgent, String browserLanguage) {

	public AuthenticationResult {
		sessionCookies = sessionCookies == null ? List.of() : List.copyOf(sessionCookies);
	}

	public static AuthenticationResult successful(List<Cookie> retrievedCookies, long executionTimeMs, String userAgent,
			String browserLanguage) {
		return new AuthenticationResult(true, "Authentication completed successfully", retrievedCookies, Instant.now(),
				executionTimeMs, userAgent, browserLanguage);
	}

	public static AuthenticationResult failed(String errorMessage, long executionTimeMs) {
		return new AuthenticationResult(false, errorMessage, List.of(), Instant.now(), executionTimeMs, null, null);
	}

}
