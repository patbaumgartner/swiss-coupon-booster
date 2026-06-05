package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Abstract base class for creating and configuring Playwright {@link Browser} instances.
 * <p>
 * Provides common logic for launching a Chromium browser with standard configuration
 * options.
 */
public abstract class AbstractBrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(AbstractBrowserFactory.class);

	/**
	 * Creates and configures a new Chromium browser instance with the specified options.
	 * @param playwrightInstance the Playwright instance to create the browser from
	 * @param headless whether to run the browser in headless mode
	 * @param slowMoMs the slow motion delay in milliseconds
	 * @param chromeArgs the list of Chrome arguments
	 * @param timeoutMs the timeout in milliseconds
	 * @return a configured {@link Browser} instance
	 */
	protected Browser createBrowser(Playwright playwrightInstance, boolean headless, double slowMoMs,
			List<String> chromeArgs, double timeoutMs) {
		if (log.isDebugEnabled()) {
			log.debug("Creating browser with headless: {}, slowMo: {}ms, args: {}", headless, slowMoMs, chromeArgs);
		}

		return playwrightInstance.chromium()
			.launch(new BrowserType.LaunchOptions().setHeadless(headless)
				.setSlowMo(slowMoMs)
				.setArgs(chromeArgs)
				.setTimeout(timeoutMs));
	}

}
