package com.patbaumgartner.migroscouponbooster.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.migroscouponbooster.properties.PlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BrowserFactory {

	private static final Logger log = LoggerFactory.getLogger(BrowserFactory.class);

	private final PlaywrightProperties browserConfiguration;

	public BrowserFactory(PlaywrightProperties browserConfiguration) {
		this.browserConfiguration = browserConfiguration;
	}

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
