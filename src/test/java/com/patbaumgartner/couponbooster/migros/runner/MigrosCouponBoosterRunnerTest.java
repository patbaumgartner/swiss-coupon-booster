package com.patbaumgartner.couponbooster.migros.runner;

import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.migros.service.CumulusCouponService;
import com.patbaumgartner.couponbooster.migros.service.MigrosAuthenticationService;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrosCouponBoosterRunnerTest {

	@Mock
	private MigrosAuthenticationService migrosAuthenticationService;

	@Mock
	private CumulusCouponService cumulusCouponService;

	@Mock
	private ApplicationArguments applicationArguments;

	@InjectMocks
	private MigrosCouponBoosterRunner migrosCouponBoosterRunner;

	@Test
	void run_withSuccessfulAuthentication_shouldActivateCoupons() throws Exception {
		// Given
		var authenticationResult = AuthenticationResult.successful(Collections.emptyList(), 100L, "userAgent", "en");
		when(migrosAuthenticationService.performAuthentication()).thenReturn(authenticationResult);

		var couponActivationResult = new CouponActivationResult(10, 0, Collections.emptyList());
		when(cumulusCouponService.activateAllAvailableCoupons(any(), any(), any())).thenReturn(couponActivationResult);

		// When
		migrosCouponBoosterRunner.run(applicationArguments);

		// Then
		verify(migrosAuthenticationService).performAuthentication();
		verify(cumulusCouponService).activateAllAvailableCoupons(authenticationResult.sessionCookies(),
				authenticationResult.userAgent(), authenticationResult.browserLanguage());
	}

	@Test
	void run_withFailedAuthentication_shouldNotActivateCoupons() throws Exception {
		// Given
		var authenticationResult = AuthenticationResult.failed("Failure", 100L);
		when(migrosAuthenticationService.performAuthentication()).thenReturn(authenticationResult);

		// When
		migrosCouponBoosterRunner.run(applicationArguments);

		// Then
		verify(migrosAuthenticationService).performAuthentication();
		verify(cumulusCouponService, never()).activateAllAvailableCoupons(any(), any(), any());
	}

}
