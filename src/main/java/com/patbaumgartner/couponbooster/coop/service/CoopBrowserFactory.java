package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.util.proxy.ProxyAddress;
import com.patbaumgartner.couponbooster.util.proxy.ProxyProperties;
import com.patbaumgartner.couponbooster.util.proxy.ProxyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and configuring Playwright {@link Browser} instances for Coop
 * Supercard automation.
 * <p>
 * This factory handles the setup of a Chromium browser with the appropriate configuration
 * for web automation tasks, including headless mode, slow motion, timeout settings,
 * anti-bot detection arguments, and proxy configuration.
 *
 * @see CoopPlaywrightProperties
 * @see ProxyProperties
 * @see ProxyResolver
 */
@Component
public class CoopBrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(CoopBrowserFactory.class);

	private final CoopPlaywrightProperties browserConfiguration;

	private final ProxyProperties proxyProperties;

	private final ProxyResolver proxyResolver;

	/**
	 * Constructs a new {@code CoopBrowserFactory} with the specified configuration.
	 * @param browserConfiguration the Playwright configuration properties
	 * @param proxyProperties the proxy configuration properties
	 * @param proxyResolver the resolver for obtaining the proxy to use for the browser
	 */
	public CoopBrowserFactory(CoopPlaywrightProperties browserConfiguration, ProxyProperties proxyProperties,
			ProxyResolver proxyResolver) {
		this.proxyProperties = proxyProperties;
		this.browserConfiguration = browserConfiguration;
		this.proxyResolver = proxyResolver;
	}

	/**
	 * Creates and configures a new Chromium browser instance with the configured options.
	 * <p>
	 * This method configures the browser with anti-bot detection measures including:
	 * <ul>
	 * <li>Custom Chrome arguments to disable automation flags</li>
	 * <li>Slow motion and typing delays to mimic human behavior</li>
	 * <li>Optional proxy support for IP rotation</li>
	 * </ul>
	 * @param playwrightInstance the Playwright instance to create the browser from
	 * @return a configured {@link Browser} instance ready for automation
	 */
	public Browser createBrowser(Playwright playwrightInstance) {
		if (log.isDebugEnabled()) {
			log.debug("Creating browser with headless: {}, slowMo: {}ms, args: {}", browserConfiguration.headless(),
					browserConfiguration.slowMoMs(), browserConfiguration.chromeArgs());
		}

		BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
		launchOptions.setHeadless(browserConfiguration.headless())
			.setSlowMo(browserConfiguration.slowMoMs())
			.setArgs(browserConfiguration.chromeArgs())
			.setTimeout(browserConfiguration.timeoutMs());

		if (proxyProperties.enabled()) {

			ProxyAddress proxy = proxyResolver.getRandomProxy();
			if (log.isDebugEnabled()) {
				log.debug("Setting proxy to browser with: {}", proxy);
			}

			launchOptions
				.setProxy(new Proxy("http://" + proxy.host() + ":" + proxy.port()).setUsername(proxy.username())
					.setPassword(proxy.password()));
		}

		return playwrightInstance.chromium().launch(launchOptions);
	}

}
