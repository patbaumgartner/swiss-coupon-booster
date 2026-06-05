package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoopCouponBoosterRunnerTest {

	@Mock
	private AuthenticationService coopAuthenticationService;

	@Mock
	private SupercardCouponService supercardCouponService;

	@Mock
	private ApplicationArguments applicationArguments;

	private CoopCouponBoosterRunner coopCouponBoosterRunner;

	@BeforeEach
	void setUp() {
		coopCouponBoosterRunner = new CoopCouponBoosterRunner(coopAuthenticationService, supercardCouponService);
	}

	@Test
	void run_withSuccessfulAuthentication_shouldActivateCoupons() throws Exception {
		// Given
		var authenticationResult = AuthenticationResult.successful(Collections.emptyList(), 100L, "userAgent", "en");
		when(coopAuthenticationService.performAuthentication()).thenReturn(authenticationResult);

		var couponActivationResult = new CouponActivationResult(10, 0, Collections.emptyList());
		when(supercardCouponService.activateAllAvailableCoupons(any(), any(), any()))
			.thenReturn(couponActivationResult);

		// When
		coopCouponBoosterRunner.run(applicationArguments);

		// Then
		verify(coopAuthenticationService).performAuthentication();
		verify(supercardCouponService).activateAllAvailableCoupons(authenticationResult.sessionCookies(),
				authenticationResult.userAgent(), authenticationResult.browserLanguage());
	}

	@Test
	void run_withFailedAuthentication_shouldNotActivateCoupons() throws Exception {
		// Given
		var authenticationResult = AuthenticationResult.failed("Failure", 100L);
		when(coopAuthenticationService.performAuthentication()).thenReturn(authenticationResult);

		// When
		coopCouponBoosterRunner.run(applicationArguments);

		// Then
		verify(coopAuthenticationService).performAuthentication();
		verify(supercardCouponService, never()).activateAllAvailableCoupons(any(), any(), any());
	}

}
