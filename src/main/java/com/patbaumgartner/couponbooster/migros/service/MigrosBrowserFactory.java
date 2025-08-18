package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.migros.properties.MigrosPlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and configuring Playwright {@link Browser} instances for Migros
 * automation.
 * <p>
 * This factory handles the setup of a Chromium browser with the appropriate configuration
 * for web automation tasks, including headless mode, slow motion, timeout settings, and
 * custom Chrome arguments.
 *
 * @see MigrosPlaywrightProperties
 */
@Component
public class MigrosBrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(MigrosBrowserFactory.class);

	private final MigrosPlaywrightProperties browserConfiguration;

	/**
	 * Constructs a new {@code MigrosBrowserFactory} with the specified configuration.
	 * @param browserConfiguration the Playwright configuration properties
	 */
	public MigrosBrowserFactory(MigrosPlaywrightProperties browserConfiguration) {
		this.browserConfiguration = browserConfiguration;
	}

	/**
	 * Creates and configures a new Chromium browser instance with the configured options.
	 * @param playwrightInstance the Playwright instance to create the browser from
	 * @return a configured {@link Browser} instance ready for automation
	 */
	public Browser createBrowser(Playwright playwrightInstance) {
		if (log.isDebugEnabled()) {
			log.debug("Creating browser with headless: {}, slowMo: {}ms, args: {}", browserConfiguration.headless(),
					browserConfiguration.slowMoMs(), browserConfiguration.chromeArgs());
		}

		return playwrightInstance.chromium()
			.launch(new BrowserType.LaunchOptions().setHeadless(browserConfiguration.headless())
				.setSlowMo(browserConfiguration.slowMoMs())
				.setArgs(browserConfiguration.chromeArgs())
				.setTimeout(browserConfiguration.timeoutMs()));
	}

}
