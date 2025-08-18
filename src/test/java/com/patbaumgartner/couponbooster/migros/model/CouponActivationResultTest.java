package com.patbaumgartner.couponbooster.migros.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CouponActivationResultTest {

	@Test
	void constructor_WithValidData_ShouldCreateResult() {
		// Given
		var successCount = 5;
		var failureCount = 2;
		var details = List.of(new CouponDetail("Coupon1", "id1", true, "Activated"),
				new CouponDetail("Coupon2", "id2", false, "Already used"));

		// When
		var result = new CouponActivationResult(successCount, failureCount, details);

		// Then
		assertThat(result.successCount()).isEqualTo(successCount);
		assertThat(result.failureCount()).isEqualTo(failureCount);
		assertThat(result.details()).hasSize(2);
		assertThat(result.details()).containsExactlyElementsOf(details);
	}

	@Test
	void constructor_WithNullDetails_ShouldCreateEmptyList() {
		// When
		var result = new CouponActivationResult(0, 0, null);

		// Then
		assertThat(result.details()).isEmpty();
	}

	@Test
	void constructor_WithMutableList_ShouldCreateImmutableCopy() {
		// Given
		var mutableDetails = new ArrayList<CouponDetail>();
		mutableDetails.add(new CouponDetail("Test", "id1", true, "OK"));

		// When
		var result = new CouponActivationResult(1, 0, mutableDetails);

		// Then
		assertThat(result.details()).hasSize(1);
		// Verify immutability by checking it's not the same instance
		assertThat(result.details()).isNotSameAs(mutableDetails);
	}

	@Test
	void successCount_ShouldReturnCorrectValue() {
		// Given
		var result = new CouponActivationResult(10, 3, List.of());

		// When & Then
		assertThat(result.successCount()).isEqualTo(10);
	}

	@Test
	void failureCount_ShouldReturnCorrectValue() {
		// Given
		var result = new CouponActivationResult(10, 3, List.of());

		// When & Then
		assertThat(result.failureCount()).isEqualTo(3);
	}

	@Test
	void details_WithMultipleItems_ShouldReturnAllDetails() {
		// Given
		var details = List.of(new CouponDetail("Coupon A", "id-a", true, "Success"),
				new CouponDetail("Coupon B", "id-b", false, "Error"),
				new CouponDetail("Coupon C", "id-c", true, "Success"));
		var result = new CouponActivationResult(2, 1, details);

		// When
		var returnedDetails = result.details();

		// Then
		assertThat(returnedDetails).hasSize(3);
		assertThat(returnedDetails.get(0).couponName()).isEqualTo("Coupon A");
		assertThat(returnedDetails.get(1).success()).isFalse();
		assertThat(returnedDetails.get(2).message()).isEqualTo("Success");
	}

}
