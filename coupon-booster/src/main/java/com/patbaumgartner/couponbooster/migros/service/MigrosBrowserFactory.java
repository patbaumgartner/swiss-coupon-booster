package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.migros.properties.MigrosPlaywrightProperties;
import com.patbaumgartner.couponbooster.service.AbstractBrowserFactory;
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
public class MigrosBrowserFactory extends AbstractBrowserFactory {

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
		return createBrowser(playwrightInstance, browserConfiguration.headless(), browserConfiguration.slowMoMs(),
				browserConfiguration.chromeArgs(), browserConfiguration.timeoutMs());
	}

}
