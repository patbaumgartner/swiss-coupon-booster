package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and configuring Playwright browser instances for Coop SuperCard
 * automation. Handles browser setup with appropriate configuration for web automation
 * tasks including headless mode, timeout settings, and browser arguments.
 */
@Component
public class CoopBrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(CoopBrowserFactory.class);

	private final CoopPlaywrightProperties browserConfiguration;

	/**
	 * Creates a new browser factory with the given configuration.
	 * @param browserConfiguration the Playwright configuration properties
	 */
	public CoopBrowserFactory(CoopPlaywrightProperties browserConfiguration) {
		this.browserConfiguration = browserConfiguration;
	}

	/**
	 * Creates and configures a new Chromium browser instance with the configured options.
	 * @param playwrightInstance the Playwright instance to create the browser from
	 * @return a configured Browser instance ready for automation
	 */
	public Browser createBrowser(Playwright playwrightInstance) {
		if (log.isDebugEnabled()) {
			log.debug("Creating browser with headless: {}, slowMo: {}ms", browserConfiguration.headless(),
					browserConfiguration.slowMoMs());
		}

		return playwrightInstance.chromium()
			.launch(new BrowserType.LaunchOptions().setHeadless(browserConfiguration.headless())
				.setSlowMo(browserConfiguration.slowMoMs())
				.setTimeout(browserConfiguration.timeoutMs()));
	}

}
