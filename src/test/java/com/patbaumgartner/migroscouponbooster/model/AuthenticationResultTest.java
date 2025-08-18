package com.patbaumgartner.migroscouponbooster.model;

import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthenticationResultTest {

	@Mock
	private Cookie mockCookie1;

	@Mock
	private Cookie mockCookie2;

	@Test
	void successful_ShouldCreateSuccessfulResult() {
		// Given
		var cookies = List.of(mockCookie1);
		var executionTime = 1500L;

		// When
		var result = AuthenticationResult.successful(cookies, executionTime);

		// Then
		assertThat(result.isSuccessful()).isTrue();
		assertThat(result.statusMessage()).isEqualTo("Authentication completed successfully");
		assertThat(result.sessionCookies()).hasSize(1);
		assertThat(result.sessionCookies()).contains(mockCookie1);
		assertThat(result.executionDurationMs()).isEqualTo(executionTime);
		assertThat(result.completionTimestamp()).isNotNull();
	}

	@Test
	void failed_ShouldCreateFailedResult() {
		// Given
		var errorMessage = "Invalid credentials";
		var executionTime = 800L;

		// When
		var result = AuthenticationResult.failed(errorMessage, executionTime);

		// Then
		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).isEqualTo(errorMessage);
		assertThat(result.sessionCookies()).isEmpty();
		assertThat(result.executionDurationMs()).isEqualTo(executionTime);
		assertThat(result.completionTimestamp()).isNotNull();
	}

	@Test
	void constructor_WithNullCookies_ShouldCreateEmptyList() {
		// Given
		var timestamp = Instant.now();

		// When
		var result = new AuthenticationResult(true, "Success", null, timestamp, 1000L);

		// Then
		assertThat(result.sessionCookies()).isEmpty();
	}

	@Test
	void constructor_WithCookiesList_ShouldCreateImmutableCopy() {
		// Given - use mutable list to test defensive copying
		var mutableCookies = new java.util.ArrayList<Cookie>();
		mutableCookies.add(mockCookie1);
		mutableCookies.add(mockCookie2);
		var timestamp = Instant.now();

		// When
		var result = new AuthenticationResult(true, "Success", mutableCookies, timestamp, 1000L);

		// Then
		assertThat(result.sessionCookies()).hasSize(2);
		assertThat(result.sessionCookies()).containsExactlyElementsOf(mutableCookies);
		// Verify defensive copying - should not be the same instance
		assertThat(result.sessionCookies()).isNotSameAs(mutableCookies);

		// Verify that modifying original list doesn't affect the result
		mutableCookies.clear();
		assertThat(result.sessionCookies()).hasSize(2); // Should still have 2 elements
	}

	@Test
	void isSuccessful_WithTrueValue_ShouldReturnTrue() {
		// Given
		var result = new AuthenticationResult(true, "Success", List.of(), Instant.now(), 1000L);

		// When & Then
		assertThat(result.isSuccessful()).isTrue();
	}

	@Test
	void isSuccessful_WithFalseValue_ShouldReturnFalse() {
		// Given
		var result = new AuthenticationResult(false, "Failed", List.of(), Instant.now(), 1000L);

		// When & Then
		assertThat(result.isSuccessful()).isFalse();
	}

	@Test
	void executionDurationMs_ShouldReturnCorrectValue() {
		// Given
		var duration = 2500L;
		var result = new AuthenticationResult(true, "Success", List.of(), Instant.now(), duration);

		// When & Then
		assertThat(result.executionDurationMs()).isEqualTo(duration);
	}

	@Test
	void statusMessage_ShouldReturnCorrectMessage() {
		// Given
		var message = "Custom status message";
		var result = new AuthenticationResult(true, message, List.of(), Instant.now(), 1000L);

		// When & Then
		assertThat(result.statusMessage()).isEqualTo(message);
	}

	@Test
	void completionTimestamp_ShouldNotBeNull() {
		// Given
		var timestamp = Instant.now();
		var result = new AuthenticationResult(true, "Success", List.of(), timestamp, 1000L);

		// When & Then
		assertThat(result.completionTimestamp()).isEqualTo(timestamp);
	}

	@Test
	void sessionCookies_WithEmptyList_ShouldReturnEmptyList() {
		// Given
		var result = new AuthenticationResult(true, "Success", List.of(), Instant.now(), 1000L);

		// When & Then
		assertThat(result.sessionCookies()).isEmpty();
	}

	@Test
	void constructor_WithDifferentTimestamps_ShouldPreserveTimestamp() {
		// Given
		var timestamp1 = Instant.now().minusSeconds(10);
		var timestamp2 = Instant.now().minusSeconds(5);

		// When
		var result1 = new AuthenticationResult(true, "Success", List.of(), timestamp1, 1000L);
		var result2 = new AuthenticationResult(false, "Failed", List.of(), timestamp2, 2000L);

		// Then
		assertThat(result1.completionTimestamp()).isEqualTo(timestamp1);
		assertThat(result2.completionTimestamp()).isEqualTo(timestamp2);
		assertThat(result1.completionTimestamp()).isNotEqualTo(result2.completionTimestamp());
	}

	@Test
	void constructor_WithZeroExecutionTime_ShouldAcceptZero() {
		// Given
		var result = new AuthenticationResult(true, "Success", List.of(), Instant.now(), 0L);

		// When & Then
		assertThat(result.executionDurationMs()).isZero();
	}

	@Test
	void constructor_WithNegativeExecutionTime_ShouldAcceptNegative() {
		// Given
		var result = new AuthenticationResult(true, "Success", List.of(), Instant.now(), -100L);

		// When & Then
		assertThat(result.executionDurationMs()).isEqualTo(-100L);
	}

}
