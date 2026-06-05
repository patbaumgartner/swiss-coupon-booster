package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.service.AbstractBrowserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Factory for creating and configuring Playwright {@link Browser} instances for Coop
 * Supercard automation.
 * <p>
 * This factory handles the setup of a Chromium browser with the appropriate configuration
 * for web automation tasks, including headless mode, slow motion, timeout settings,
 * anti-bot detection arguments.
 *
 * @see CoopPlaywrightProperties
 */
@Component
public class CoopBrowserFactory extends AbstractBrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(CoopBrowserFactory.class);

	private final CoopPlaywrightProperties browserConfiguration;

	/**
	 * Constructs a new {@code CoopBrowserFactory} with the specified configuration.
	 * @param browserConfiguration the Playwright configuration properties
	 */
	public CoopBrowserFactory(CoopPlaywrightProperties browserConfiguration) {
		this.browserConfiguration = browserConfiguration;
	}

	/**
	 * Creates and configures a new BrowserContext, either persistent or ephemeral based
	 * on configuration.
	 * @param playwright the Playwright instance
	 * @param contextOptions options for the browser context (e.g. stealth settings)
	 * @return a handle containing the context and optionally the browser
	 */
	public BrowserContextHandle createBrowserContext(Playwright playwright, Browser.NewContextOptions contextOptions) {
		if (browserConfiguration.userDataDir() != null && !browserConfiguration.userDataDir().isBlank()) {
			log.info("Using persistent browser context with user data dir: {}", browserConfiguration.userDataDir());

			BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions();

			// Map launch options
			options.setHeadless(browserConfiguration.headless())
				.setSlowMo(browserConfiguration.slowMoMs())
				.setArgs(browserConfiguration.chromeArgs())
				.setTimeout(browserConfiguration.timeoutMs());

			// Map context options
			if (contextOptions != null) {
				if (contextOptions.viewportSize != null) {
					contextOptions.viewportSize.ifPresent(options::setViewportSize);
				}
				if (contextOptions.locale != null) {
					options.setLocale(contextOptions.locale);
				}
				if (contextOptions.timezoneId != null) {
					options.setTimezoneId(contextOptions.timezoneId);
				}
				if (contextOptions.permissions != null) {
					options.setPermissions(contextOptions.permissions);
				}
				if (contextOptions.geolocation != null) {
					options.setGeolocation(contextOptions.geolocation);
				}
				if (contextOptions.deviceScaleFactor != null) {
					options.setDeviceScaleFactor(contextOptions.deviceScaleFactor);
				}
				if (contextOptions.isMobile != null) {
					options.setIsMobile(contextOptions.isMobile);
				}
				if (contextOptions.hasTouch != null) {
					options.setHasTouch(contextOptions.hasTouch);
				}
				if (contextOptions.colorScheme != null) {
					contextOptions.colorScheme.ifPresent(options::setColorScheme);
				}
				if (contextOptions.userAgent != null) {
					options.setUserAgent(contextOptions.userAgent);
				}
			}

			BrowserContext context = playwright.chromium()
				.launchPersistentContext(Path.of(browserConfiguration.userDataDir()), options);
			return BrowserContextHandle.persistent(context);
		}
		else {
			log.debug("Using ephemeral browser context");
			Browser browser = createBrowser(playwright);
			BrowserContext context = browser.newContext(contextOptions);
			return BrowserContextHandle.ephemeral(context, browser);
		}
	}

	private Browser createBrowser(Playwright playwrightInstance) {
		if (log.isDebugEnabled()) {
			log.debug("Creating browser with headless: {}, slowMo: {}ms, args: {}", browserConfiguration.headless(),
					browserConfiguration.slowMoMs(), browserConfiguration.chromeArgs());
		}

		BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
		launchOptions.setHeadless(browserConfiguration.headless())
			.setSlowMo(browserConfiguration.slowMoMs())
			.setArgs(browserConfiguration.chromeArgs())
			.setTimeout(browserConfiguration.timeoutMs());

		return playwrightInstance.chromium().launch(launchOptions);
	}

}
