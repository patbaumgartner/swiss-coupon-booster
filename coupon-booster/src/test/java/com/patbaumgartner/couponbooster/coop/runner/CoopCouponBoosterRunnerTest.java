package com.patbaumgartner.couponbooster.coop.runner;

import com.patbaumgartner.couponbooster.coop.service.SupercardCouponService;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.runner.ActivationExitCode;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoopCouponBoosterRunnerTest {

	@Mock
	private AuthenticationService coopAuthenticationService;

	@Mock
	private SupercardCouponService supercardCouponService;

	@Mock
	private ApplicationArguments applicationArguments;

	private ActivationExitCode exitCode;

	private CoopCouponBoosterRunner runner;

	@BeforeEach
	void setUp() {
		exitCode = new ActivationExitCode();
		runner = new CoopCouponBoosterRunner(coopAuthenticationService, supercardCouponService, exitCode);
	}

	@Test
	void successfulAuthentication_activatesCouponsAndExitsZero() throws Exception {
		var authentication = AuthenticationResult.successful(List.of(), 100L, "userAgent", "en");
		when(coopAuthenticationService.performAuthentication()).thenReturn(authentication);
		when(supercardCouponService.activateAllAvailableCoupons(any(), any(), any()))
			.thenReturn(new CouponActivationResult(10, 0, List.of()));

		runner.run(applicationArguments);

		verify(supercardCouponService).activateAllAvailableCoupons(authentication.sessionCookies(),
				authentication.userAgent(), authentication.browserLanguage());
		assertThat(exitCode.getExitCode()).isZero();
	}

	@Test
	void failedAuthentication_skipsActivationAndExitsNonZero() throws Exception {
		when(coopAuthenticationService.performAuthentication())
			.thenReturn(AuthenticationResult.failed("Credentials missing", 100L));

		runner.run(applicationArguments);

		verify(supercardCouponService, never()).activateAllAvailableCoupons(any(), any(), any());
		assertThat(exitCode.getExitCode()).isEqualTo(1);
	}

	@Test
	void couponLevelFailuresDoNotFailTheProcess() throws Exception {
		when(coopAuthenticationService.performAuthentication())
			.thenReturn(AuthenticationResult.successful(List.of(), 100L, "ua", "en"));
		when(supercardCouponService.activateAllAvailableCoupons(any(), any(), any()))
			.thenReturn(new CouponActivationResult(0, 3, List.of()));

		runner.run(applicationArguments);

		assertThat(exitCode.getExitCode()).isZero();
	}

}
