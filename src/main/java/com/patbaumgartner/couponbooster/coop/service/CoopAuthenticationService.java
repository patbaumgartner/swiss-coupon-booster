package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

/**
 * Coop SuperCard authentication service using Playwright browser automation. Handles
 * login flow and session cookie extraction.
 */
@Service
public class CoopAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(CoopAuthenticationService.class);

	private final CoopUserProperties userCredentials;

	private final CoopPlaywrightProperties browserConfiguration;

	private final CoopSelectorsProperties elementSelectors;

	private final CoopBrowserFactory browserCreator;

	/**
	 * Creates authentication service with required dependencies.
	 */
	public CoopAuthenticationService(CoopUserProperties userCredentials, CoopPlaywrightProperties browserConfiguration,
			CoopSelectorsProperties elementSelectors, CoopBrowserFactory browserCreator) {
		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.browserConfiguration = Objects.requireNonNull(browserConfiguration,
				"Browser configuration cannot be null");
		this.elementSelectors = Objects.requireNonNull(elementSelectors, "Element selectors cannot be null");
		this.browserCreator = Objects.requireNonNull(browserCreator, "Browser factory cannot be null");
	}

	/**
	 * Authenticates user and extracts session cookies.
	 * @return authentication result with cookies or error details
	 */
	@Override
	public AuthenticationResult performAuthentication() {
		var startTime = System.currentTimeMillis();

		try {
			validateUserCredentials();
			return executeAuthenticationFlow(startTime);
		}
		catch (Exception authenticationException) {
			var executionDuration = System.currentTimeMillis() - startTime;
			log.error("Authentication failed: {}", authenticationException.getMessage());
			return AuthenticationResult.failed(authenticationException.getMessage(), executionDuration);
		}
	}

	private AuthenticationResult executeAuthenticationFlow(long startTime) {
		try (var playwright = Playwright.create()) {
			var browser = browserCreator.createBrowser(playwright);

			try (var context = browser.newContext()) {
				var page = context.newPage();

				// Add the DataDome cookie before navigation
				context.addCookies(Arrays.asList(new Cookie[] {
						new Cookie("datadome", browserConfiguration.datadomeCookieValue()).setDomain(".supercard.ch")
							.setPath("/") // usually "/"
							.setHttpOnly(false)
							.setSecure(true) }));

				performLoginFlow(page);

				var cookies = context.cookies();
				log.debug("Retrieved {} session cookies", cookies.size());

				var executionDuration = System.currentTimeMillis() - startTime;
				return AuthenticationResult.successful(cookies, executionDuration);
			}
			finally {
				browser.close();
			}
		}
	}

	private void validateUserCredentials() {
		if (isBlank(userCredentials.email()) || isBlank(userCredentials.password())) {
			throw new CouponBoosterException(
					"User credentials are missing. Configure coop.user.email and coop.user.password");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void performLoginFlow(Page page) {
		log.debug("Starting login flow");

		try {
			navigateToLoginPage(page);
			handleCookieConsent(page);
			clickLoginLink(page);
			enterCredentialsAndSubmit(page);

			log.debug("Login flow completed");
		}
		catch (Exception flowException) {
			log.error("Login flow failed: {}", flowException.getMessage());
			throw new CouponBoosterException("Login flow failed: " + flowException.getMessage(), flowException);
		}
	}

	private void navigateToLoginPage(Page page) {
		log.debug("Navigating to: {}", browserConfiguration.loginUrl());
		page.navigate(browserConfiguration.loginUrl());
		page.waitForLoadState(LoadState.NETWORKIDLE);
	}

	private void handleCookieConsent(Page page) {
		try {
			var acceptButton = page.locator(elementSelectors.cookieAcceptButton());
			acceptButton.first().waitFor(new Locator.WaitForOptions().setTimeout(3000));

			if (acceptButton.first().isVisible()) {
				acceptButton.first().click();
				page.waitForLoadState(LoadState.NETWORKIDLE);
				log.debug("Accepted cookies");
			}
		}
		catch (TimeoutError e) {
			log.warn("Cookie consent dialog not found or not needed, continuing: {}", e.getMessage());
		}
	}

	private void clickLoginLink(Page page) {
		clickElement(page, elementSelectors.loginLink());
	}

	private void enterCredentialsAndSubmit(Page page) {
		typeIntoField(page, elementSelectors.usernameInput(), userCredentials.email());
		typeIntoField(page, elementSelectors.passwordInput(), userCredentials.password());
		clickElement(page, elementSelectors.submitButton());
	}

	private void typeIntoField(Page page, String selector, String text) {
		var field = findElement(page, selector);
		field.clear();
		field.type(text, new Locator.TypeOptions().setDelay(browserConfiguration.typingDelayMs()));
	}

	private void clickElement(Page page, String selector) {
		findElement(page, selector).click();
	}

	private Locator findElement(Page page, String selector) {
		try {
			var element = page.locator(selector);
			element.first().waitFor(new Locator.WaitForOptions().setTimeout(2000));

			if (!element.first().isVisible()) {
				throw new CouponBoosterException("Element not visible: " + selector);
			}

			return element;
		}
		catch (Exception e) {
			throw new CouponBoosterException("Element not found: " + selector, e);
		}
	}

}
