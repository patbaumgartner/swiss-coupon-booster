package com.patbaumgartner.migroscouponbooster.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponDetailTest {

	@Test
	void constructor_WithAllFields_ShouldCreateCorrectDetail() {
		// Given
		var couponName = "20% off groceries";
		var couponId = "coup_12345";
		var success = true;
		var message = "Successfully activated";

		// When
		var detail = new CouponDetail(couponName, couponId, success, message);

		// Then
		assertThat(detail.couponName()).isEqualTo(couponName);
		assertThat(detail.couponId()).isEqualTo(couponId);
		assertThat(detail.success()).isTrue();
		assertThat(detail.message()).isEqualTo(message);
	}

	@Test
	void success_WithTrueValue_ShouldReturnTrue() {
		// Given
		var detail = new CouponDetail("Test", "id", true, "OK");

		// When & Then
		assertThat(detail.success()).isTrue();
	}

	@Test
	void success_WithFalseValue_ShouldReturnFalse() {
		// Given
		var detail = new CouponDetail("Test", "id", false, "Failed");

		// When & Then
		assertThat(detail.success()).isFalse();
	}

	@Test
	void couponName_ShouldReturnCorrectName() {
		// Given
		var name = "Free shipping coupon";
		var detail = new CouponDetail(name, "id", true, "OK");

		// When & Then
		assertThat(detail.couponName()).isEqualTo(name);
	}

	@Test
	void couponId_ShouldReturnCorrectId() {
		// Given
		var id = "coupon_987654321";
		var detail = new CouponDetail("Test", id, true, "OK");

		// When & Then
		assertThat(detail.couponId()).isEqualTo(id);
	}

	@Test
	void message_ShouldReturnCorrectMessage() {
		// Given
		var message = "Coupon already expired";
		var detail = new CouponDetail("Test", "id", false, message);

		// When & Then
		assertThat(detail.message()).isEqualTo(message);
	}

	@Test
	void constructor_WithNullValues_ShouldHandleNulls() {
		// When
		var detail = new CouponDetail(null, null, false, null);

		// Then
		assertThat(detail.couponName()).isNull();
		assertThat(detail.couponId()).isNull();
		assertThat(detail.success()).isFalse();
		assertThat(detail.message()).isNull();
	}

	@Test
	void constructor_WithEmptyStrings_ShouldHandleEmpty() {
		// When
		var detail = new CouponDetail("", "", true, "");

		// Then
		assertThat(detail.couponName()).isEmpty();
		assertThat(detail.couponId()).isEmpty();
		assertThat(detail.success()).isTrue();
		assertThat(detail.message()).isEmpty();
	}

}
