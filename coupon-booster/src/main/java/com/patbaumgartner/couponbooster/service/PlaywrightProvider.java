package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Component;

/**
 * Provider for creating Playwright instances.
 * <p>
 * This class wraps {@link Playwright#create()} to facilitate unit testing via mocking.
 */
@Component
public class PlaywrightProvider {

	/**
	 * Creates a new Playwright instance.
	 * @return a new Playwright instance
	 */
	public Playwright create() {
		return Playwright.create();
	}

}
