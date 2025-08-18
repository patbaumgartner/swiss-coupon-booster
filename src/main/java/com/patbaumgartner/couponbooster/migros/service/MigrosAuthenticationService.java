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
 * {@link AuthenticationService} implementation for Migros accounts using Playwright for
 * browser automation.
 * <p>
 * This service handles the complete authentication flow for the Migros website,
 * including:
 * <ul>
 * <li>Navigating to the login page.</li>
 * <li>Handling cookie consent dialogs.</li>
 * <li>Entering user credentials (email and password) in a multi-step process.</li>
 * <li>Disabling WebAuthn to ensure the password field is available.</li>
 * <li>Submitting the login form.</li>
 * <li>Extracting session cookies upon successful authentication.</li>
 * </ul>
 * It is highly configurable through properties for user credentials, Playwright settings,
 * and element selectors.
 *
 * @see MigrosUserProperties
 * @see MigrosPlaywrightProperties
 * @see MigrosSelectorsProperties
 * @see MigrosBrowserFactory
 * @see WebAuthnDisabler
 */
@Service
public class MigrosAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(MigrosAuthenticationService.class);

	private final MigrosUserProperties userCredentials;

	private final MigrosPlaywrightProperties browserConfiguration;

	private final MigrosSelectorsProperties elementSelectors;

	private final MigrosBrowserFactory browserCreator;

	private final WebAuthnDisabler webAuthnDisabler;

	/**
	 * Constructs a new {@code MigrosAuthenticationService} with the specified
	 * dependencies.
	 * @param userCredentials the user's login credentials (email and password)
	 * @param browserConfiguration the configuration for the Playwright browser instance
	 * @param elementSelectors the CSS selectors for locating elements on the page
	 * @param browserCreator the factory for creating Playwright browser instances
	 * @param webAuthnDisabler the utility to disable WebAuthn
	 */
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

	/**
	 * Performs the authentication flow for the Migros website.
	 * @return an {@link AuthenticationResult} containing the session cookies if
	 * successful, or an error message if the authentication fails.
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
			log.error("Authentication process failed: {}", authenticationException.getMessage(),
					authenticationException);
			return AuthenticationResult.failed(authenticationException.getMessage(), executionDuration);
		}
	}

	private AuthenticationResult executeAuthenticationFlow(long startTime) {
		try (var playwright = Playwright.create()) {

			try (var browser = browserCreator.createBrowser(playwright); var context = browser.newContext()) {
				var page = context.newPage();

				// Extract user-agent and browser language
				String userAgent = page.evaluate("() => navigator.userAgent").toString();
				String browserLanguage = page.evaluate("() => navigator.language").toString();

				if (log.isDebugEnabled()) {
					log.debug("Browser user-agent: {}", userAgent);
					log.debug("Browser language: {}", browserLanguage);
				}

				disableWebAuthn(page);
				performLoginFlow(page);

				var cookies = context.cookies();
				if (log.isDebugEnabled()) {
					log.debug("Retrieved {} cookies from browser context", cookies.size());
				}

				var executionDuration = System.currentTimeMillis() - startTime;
				return AuthenticationResult.successful(cookies, executionDuration, userAgent, browserLanguage);
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
		if (log.isDebugEnabled()) {
			log.debug("Navigating to login page: {}", browserConfiguration.loginUrl());
		}
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
			log.error("Cookie consent dialog not found or not needed, continuing: {}", e.getMessage(), e);
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
			log.error("Timeout waiting for password page navigation, continuing anyway: {}", e.getMessage(), e);
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
			log.error("Failed to disable WebAuthn (non-critical): {}", e.getMessage(), e);
		}
	}

}
