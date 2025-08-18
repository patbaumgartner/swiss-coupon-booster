package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.migros.properties.MigrosPlaywrightProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosSelectorsProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.util.WebAuthnDisabler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Playwright-based implementation of authentication service for Migros accounts. Handles
 * the complete login flow including cookie consent, email/password entry, and session
 * cookie extraction.
 */
@Service
public class MigrosAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(MigrosAuthenticationService.class);

	private final MigrosUserProperties userCredentials;

	private final MigrosPlaywrightProperties browserConfiguration;

	private final MigrosSelectorsProperties elementSelectors;

	private final MigrosBrowserFactory browserCreator;

	private final WebAuthnDisabler webAuthnDisabler;

	public MigrosAuthenticationService(MigrosUserProperties userCredentials,
			MigrosPlaywrightProperties browserConfiguration, MigrosSelectorsProperties elementSelectors,
			MigrosBrowserFactory browserCreator, WebAuthnDisabler webAuthnDisabler) {

		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.browserConfiguration = Objects.requireNonNull(browserConfiguration,
				"Browser configuration cannot be null");
		this.elementSelectors = Objects.requireNonNull(elementSelectors, "Element selectors cannot be null");
		this.browserCreator = Objects.requireNonNull(browserCreator, "Browser factory cannot be null");
		this.webAuthnDisabler = Objects.requireNonNull(webAuthnDisabler, "WebAuthn disabler cannot be null");
	}

	@Override
	public AuthenticationResult performAuthentication() {
		var startTime = System.currentTimeMillis();

		try {
			validateUserCredentials();
			return executeAuthenticationFlow(startTime);
		}
		catch (Exception authenticationException) {
			var executionDuration = System.currentTimeMillis() - startTime;
			log.error("Authentication process failed: {}", authenticationException.getMessage(),
					authenticationException);
			return AuthenticationResult.failed(authenticationException.getMessage(), executionDuration);
		}
	}

	private AuthenticationResult executeAuthenticationFlow(long startTime) {
		try (var playwright = Playwright.create()) {
			var browser = browserCreator.createBrowser(playwright);

			try (var context = browser.newContext()) {
				var page = context.newPage();

				disableWebAuthn(page);
				performLoginFlow(page);

				var cookies = context.cookies();
				log.debug("Retrieved {} cookies from browser context", cookies.size());

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
					"User credentials are missing. Configure migros.user.email and migros.user.password");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void performLoginFlow(Page page) {
		log.debug("Starting authentication flow sequence");

		try {
			navigateToLoginPage(page);
			handleCookieConsent(page);
			enterEmailAndContinue(page);
			clickPasswordLoginOption(page);
			waitForPasswordPage(page);
			disableWebAuthn(page);
			enterPasswordAndLogin(page);
			waitForLoginSuccess(page);

			log.debug("Authentication flow completed successfully");
		}
		catch (Exception flowException) {
			log.error("Authentication flow failed: {}", flowException.getMessage(), flowException);
			throw new CouponBoosterException("Authentication flow failed: " + flowException.getMessage(),
					flowException);
		}
	}

	private void navigateToLoginPage(Page page) {
		log.debug("Navigating to login page: {}", browserConfiguration.loginUrl());
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
				log.debug("Cookie consent accepted");
			}
		}
		catch (TimeoutError e) {
			log.warn("Cookie consent dialog not found or not needed, continuing: {}", e.getMessage());
		}
	}

	private void enterEmailAndContinue(Page page) {
		typeIntoField(page, elementSelectors.emailInput(), userCredentials.email());
		clickElement(page, elementSelectors.submitButton());
	}

	private void clickPasswordLoginOption(Page page) {
		clickElement(page, elementSelectors.passwordLoginLink());
	}

	private void waitForPasswordPage(Page page) {
		try {
			page.waitForURL(browserConfiguration.passwordUrl() + "*",
					new Page.WaitForURLOptions().setTimeout(browserConfiguration.timeoutMs()));
			log.debug("Successfully navigated to password page");
		}
		catch (Exception e) {
			log.warn("Timeout waiting for password page navigation, continuing anyway: {}", e.getMessage());
		}
	}

	private void enterPasswordAndLogin(Page page) {
		typeIntoField(page, elementSelectors.passwordInput(), userCredentials.password());
		clickElement(page, elementSelectors.submitButton());
	}

	private void waitForLoginSuccess(Page page) {
		page.waitForLoadState(LoadState.NETWORKIDLE);

		if (page.url().contains("login.migros.ch")) {
			throw new CouponBoosterException(
					"Authentication failed - still on login page after credentials submission");
		}

		log.debug("Successfully authenticated and redirected away from login page");
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
			throw new CouponBoosterException("Failed to find element with selector '" + selector + "'. "
					+ "This may indicate a UI change or timing issue.", e);
		}
	}

	private void disableWebAuthn(Page page) {
		try {
			page.addInitScript(webAuthnDisabler.getDisableScript());
			log.debug("WebAuthn disabled");
		}
		catch (Exception e) {
			log.warn("Failed to disable WebAuthn (non-critical): {}", e.getMessage());
		}
	}

}
