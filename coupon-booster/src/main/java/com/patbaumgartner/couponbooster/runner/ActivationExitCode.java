package com.patbaumgartner.couponbooster.runner;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Reports the outcome of the one-shot startup runs as the process exit code.
 * <p>
 * Without this the container always exited {@code 0}, so a cron job or a
 * {@code restart: on-failure} policy could not tell a successful activation from a failed
 * login. Exit code {@code 1} means at least one provider could not authenticate (wrong
 * credentials, sidecar unreachable, bot challenge). Individual coupons failing to
 * activate is reported in the run summary but is not a process failure: coupons expire
 * and are withdrawn between runs, which is normal.
 */
@Component
public class ActivationExitCode implements ExitCodeGenerator {

	private final AtomicBoolean authenticationFailed = new AtomicBoolean(false);

	/**
	 * Records that a provider failed to authenticate.
	 */
	public void recordAuthenticationFailure() {
		this.authenticationFailed.set(true);
	}

	@Override
	public int getExitCode() {
		return this.authenticationFailed.get() ? 1 : 0;
	}

}
