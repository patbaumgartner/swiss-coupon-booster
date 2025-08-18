package com.patbaumgartner.couponbooster.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponBoosterExceptionTest {

	@Test
	void constructor_WithMessage_ShouldCreateExceptionWithMessage() {
		// Given
		var message = "Authentication failed";

		// When
		var exception = new CouponBoosterException(message);

		// Then
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void constructor_WithMessageAndCause_ShouldCreateExceptionWithBoth() {
		// Given
		var message = "Network connection failed";
		var cause = new RuntimeException("Connection timeout");

		// When
		var exception = new CouponBoosterException(message, cause);

		// Then
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getCause()).isEqualTo(cause);
	}

	@Test
	void constructor_WithNullMessage_ShouldHandleNull() {
		// When
		var exception = new CouponBoosterException(null);

		// Then
		assertThat(exception.getMessage()).isNull();
	}

	@Test
	void constructor_WithEmptyMessage_ShouldHandleEmpty() {
		// When
		var exception = new CouponBoosterException("");

		// Then
		assertThat(exception.getMessage()).isEmpty();
	}

	@Test
	void constructor_WithNullCause_ShouldHandleNullCause() {
		// Given
		var message = "Something went wrong";

		// When
		var exception = new CouponBoosterException(message, null);

		// Then
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void throwException_ShouldBeRuntimeException() {
		// Given
		var message = "Test exception";

		// When & Then
		assertThatThrownBy(() -> {
			throw new CouponBoosterException(message);
		}).isInstanceOf(RuntimeException.class).isInstanceOf(CouponBoosterException.class).hasMessage(message);
	}

	@Test
	void throwExceptionWithCause_ShouldPreserveCause() {
		// Given
		var message = "Wrapper exception";
		var cause = new IllegalArgumentException("Invalid argument");

		// When & Then
		assertThatThrownBy(() -> {
			throw new CouponBoosterException(message, cause);
		}).isInstanceOf(CouponBoosterException.class).hasMessage(message).hasCause(cause);
	}

	@Test
	void exception_ShouldBeSerializable() {
		// Given
		var exception = new CouponBoosterException("Test message");

		// When & Then
		assertThat(exception).isInstanceOf(java.io.Serializable.class);
	}

}
