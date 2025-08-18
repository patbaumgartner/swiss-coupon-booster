package com.patbaumgartner.couponbooster;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

/**
 * Integration test for the Coupon Booster Spring Boot application. Verifies that the
 * application context loads successfully with all components properly configured.
 */
@SpringBootTest(webEnvironment = NONE)
@ActiveProfiles("test")
class CouponBoosterApplicationTests {

	/**
	 * Tests that the Spring application context loads without errors. This verifies that
	 * all beans are properly configured and can be instantiated.
	 */
	@Test
	void contextLoads() {
		// Context loading is implicit - if this test passes, the context loaded
		// successfully
	}

}
