package com.patbaumgartner.couponbooster.model;

import com.microsoft.playwright.options.Cookie;

import java.time.Instant;
import java.util.List;

public record AuthenticationResult(boolean isSuccessful, String statusMessage, List<Cookie> sessionCookies,
		Instant completionTimestamp, long executionDurationMs, String userAgent, String browserLanguage) {

	public AuthenticationResult(boolean isSuccessful, String statusMessage, List<Cookie> sessionCookies,
			Instant completionTimestamp, long executionDurationMs) {
		this(isSuccessful, statusMessage, sessionCookies, completionTimestamp, executionDurationMs, null, null);
	}

	public AuthenticationResult(boolean isSuccessful, String statusMessage, List<Cookie> sessionCookies,
			Instant completionTimestamp, long executionDurationMs, String userAgent, String browserLanguage) {
		this.isSuccessful = isSuccessful;
		this.statusMessage = statusMessage;
		this.sessionCookies = sessionCookies == null ? List.of() : List.copyOf(sessionCookies);
		this.completionTimestamp = completionTimestamp;
		this.executionDurationMs = executionDurationMs;
		this.userAgent = userAgent;
		this.browserLanguage = browserLanguage;
	}

	public List<Cookie> sessionCookies() {
		return sessionCookies; // Already immutable from constructor
	}

	public static AuthenticationResult successful(List<Cookie> retrievedCookies, long executionTimeMs) {
		return new AuthenticationResult(true, "Authentication completed successfully", retrievedCookies, Instant.now(),
				executionTimeMs);
	}

	public static AuthenticationResult successful(List<Cookie> retrievedCookies, long executionTimeMs, String userAgent,
			String browserLanguage) {
		return new AuthenticationResult(true, "Authentication completed successfully", retrievedCookies, Instant.now(),
				executionTimeMs, userAgent, browserLanguage);
	}

	public static AuthenticationResult failed(String errorMessage, long executionTimeMs) {
		return new AuthenticationResult(false, errorMessage, List.of(), Instant.now(), executionTimeMs);
	}

	public static AuthenticationResult failed(String errorMessage, long executionTimeMs, String userAgent,
			String browserLanguage) {
		return new AuthenticationResult(false, errorMessage, List.of(), Instant.now(), executionTimeMs, userAgent,
				browserLanguage);
	}
}
