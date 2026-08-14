package com.patbaumgartner.couponbooster.scheduler;

import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.model.SessionCookie;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.service.CouponService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AbstractCouponBoosterSchedulerTest {

	private static final AuthenticationResult SUCCESS = AuthenticationResult
		.successful(List.of(new SessionCookie("s", "v", ".x.ch")), 42L, "ua", "de-CH");

	@Test
	void reportsAuthenticationFailureWithoutActivatingCoupons() {
		var couponService = new RecordingCouponService();
		var scheduler = new TestScheduler(() -> AuthenticationResult.failed("Credentials missing", 7L), couponService);

		ActivationOutcome outcome = scheduler.runActivation().orElseThrow();

		assertThat(outcome.authenticated()).isFalse();
		assertThat(outcome.message()).isEqualTo("Credentials missing");
		assertThat(outcome.authDurationMs()).isEqualTo(7L);
		assertThat(outcome.activated()).isZero();
		assertThat(couponService.invocations.get()).isZero();
	}

	@Test
	void reportsCountsFromASuccessfulRun() {
		var couponService = new RecordingCouponService(new CouponActivationResult(5, 2, List.of()));
		var scheduler = new TestScheduler(() -> SUCCESS, couponService);

		ActivationOutcome outcome = scheduler.runActivation().orElseThrow();

		assertThat(outcome.provider()).isEqualTo("Test");
		assertThat(outcome.authenticated()).isTrue();
		assertThat(outcome.activated()).isEqualTo(5);
		assertThat(outcome.failed()).isEqualTo(2);
		assertThat(outcome.message()).isEqualTo("Activation completed");
	}

	@Test
	void rejectsAnOverlappingRunWhileOneIsInFlight() throws Exception {
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var couponService = new RecordingCouponService();
		var scheduler = new TestScheduler(() -> {
			started.countDown();
			try {
				release.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return SUCCESS;
		}, couponService);

		var overlapping = new AtomicReference<Optional<ActivationOutcome>>();
		var first = Thread.ofVirtual().start(scheduler::runActivation);
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

		overlapping.set(scheduler.runActivation());

		release.countDown();
		first.join();

		assertThat(overlapping.get()).isEmpty();
		assertThat(couponService.invocations.get()).isEqualTo(1);
	}

	@Test
	void releasesTheGuardAfterARunCompletes() {
		var scheduler = new TestScheduler(() -> SUCCESS, new RecordingCouponService());

		assertThat(scheduler.runActivation()).isPresent();
		assertThat(scheduler.runActivation()).isPresent();
	}

	@Test
	void releasesTheGuardWhenTheRunThrows() {
		var attempts = new AtomicInteger();
		var scheduler = new TestScheduler(() -> {
			if (attempts.getAndIncrement() == 0) {
				throw new IllegalStateException("sidecar exploded");
			}
			return SUCCESS;
		}, new RecordingCouponService());

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(scheduler::runActivation);

		// A crashed run must not wedge the provider for the rest of the process life.
		assertThat(scheduler.runActivation()).isPresent();
	}

	private static final class RecordingCouponService implements CouponService {

		private final AtomicInteger invocations = new AtomicInteger();

		private final CouponActivationResult result;

		RecordingCouponService() {
			this(new CouponActivationResult(0, 0, List.of()));
		}

		RecordingCouponService(CouponActivationResult result) {
			this.result = result;
		}

		@Override
		public CouponActivationResult activateAllAvailableCoupons(List<SessionCookie> sessionCookies, String userAgent,
				String language) {
			invocations.incrementAndGet();
			return result;
		}

	}

	private static final class TestScheduler extends AbstractCouponBoosterScheduler {

		TestScheduler(AuthenticationService authenticationService, CouponService couponService) {
			super(authenticationService, couponService, "Test");
		}

	}

}
