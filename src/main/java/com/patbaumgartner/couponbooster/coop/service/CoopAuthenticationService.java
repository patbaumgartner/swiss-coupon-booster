package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.util.proxy.ProxyProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;

import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.CookieNames.DATADOME_COOKIE;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.CookieNames.WILDCARD_COOKIE_DOMAIN;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.Datadome.CAPTCHA_DELIVERY_URL;

/**
 * {@link AuthenticationService} implementation for Coop Supercard using Playwright for
 * browser automation.
 * <p>
 * This service handles the entire authentication flow for the Coop website, including:
 * <ul>
 * <li>Navigating to the login page.</li>
 * <li>Handling cookie consent dialogs.</li>
 * <li>Entering user credentials (email and password).</li>
 * <li>Submitting the login form.</li>
 * <li>Handling Datadome CAPTCHA challenges by integrating with a
 * {@link DatadomeCaptchaResolver}.</li>
 * <li>Extracting session cookies upon successful authentication.</li>
 * </ul>
 * It is highly configurable through properties for user credentials, Playwright settings,
 * and element selectors.
 *
 * @see DatadomeCaptchaResolver
 * @see ProxyProperties
 * @see CoopUserProperties
 * @see CoopPlaywrightProperties
 * @see CoopSelectorsProperties
 * @see CoopBrowserFactory
 */
@Service
public class CoopAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(CoopAuthenticationService.class);

	private final DatadomeCaptchaResolver datadomeCaptchaResolver;

	private final ProxyProperties proxyProperties;

	private final CoopUserProperties userCredentials;

	private final CoopPlaywrightProperties browserConfiguration;

	private final CoopSelectorsProperties elementSelectors;

	private final CoopBrowserFactory browserCreator;

	/**
	 * Constructs a new {@code CoopAuthenticationService} with the specified dependencies.
	 * @param datadomeCaptchaResolver the resolver for handling Datadome CAPTCHA
	 * challenges
	 * @param userCredentials the user's login credentials (email and password)
	 * @param browserConfiguration the configuration for the Playwright browser instance
	 * @param elementSelectors the CSS selectors for locating elements on the page
	 * @param browserCreator the factory for creating Playwright browser instances
	 */
	public CoopAuthenticationService(DatadomeCaptchaResolver datadomeCaptchaResolver, ProxyProperties proxyProperties,
			CoopUserProperties userCredentials, CoopPlaywrightProperties browserConfiguration,
			CoopSelectorsProperties elementSelectors, CoopBrowserFactory browserCreator) {
		this.datadomeCaptchaResolver = Objects.requireNonNull(datadomeCaptchaResolver,
				"Datadome captcha resolver cannot be null");
		this.proxyProperties = Objects.requireNonNull(proxyProperties, "Proxy properties cannot be null");
		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.browserConfiguration = Objects.requireNonNull(browserConfiguration,
				"Browser configuration cannot be null");
		this.elementSelectors = Objects.requireNonNull(elementSelectors, "Element selectors cannot be null");
		this.browserCreator = Objects.requireNonNull(browserCreator, "Browser factory cannot be null");
	}

	/**
	 * Performs the authentication flow for the Coop website.
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
			log.error("Authentication failed: {}", authenticationException.getMessage(), authenticationException);
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

				if (proxyProperties.enabled()) {
					log.info("Proxy is enabled, registering route to handle Datadome captcha.");

					// Listen for requests and fetch cookie from 2captcha
					page.route(CAPTCHA_DELIVERY_URL, route -> {
						Request request = route.request();
						String url = request.url();

						if (url.contains("t=bv")) {
							throw new CouponBoosterException("Proxy IP is blocked by Datadome (t=bv in URL)");
						}

						var parsedCookie = datadomeCaptchaResolver.resolveCaptcha(url, page.url(), userAgent);

						context.addCookies(Collections.singletonList(
								new Cookie(parsedCookie.name(), parsedCookie.value()).setDomain(parsedCookie.domain())
									.setPath(parsedCookie.path() != null ? parsedCookie.path() : "/")
									.setHttpOnly(parsedCookie.httpOnly())
									.setSecure(parsedCookie.secure())));

						// Let the request continue after cookies are set
						route.resume();
					});
				}
				// Add the DataDome cookie from the configuration - for development
				if (context.cookies().stream().noneMatch(cookie -> DATADOME_COOKIE.equals(cookie.name))) {
					context.addCookies(Collections
						.singletonList(new Cookie(DATADOME_COOKIE, browserConfiguration.datadomeCookieValue())
							.setDomain(WILDCARD_COOKIE_DOMAIN)
							.setPath("/") // usually "/"
							.setHttpOnly(false)
							.setSecure(true)));
				}

				performLoginFlow(page);

				var cookies = context.cookies();
				if (log.isDebugEnabled()) {
					log.debug("Retrieved {} session cookies", cookies.size());
				}

				var executionDuration = System.currentTimeMillis() - startTime;
				return AuthenticationResult.successful(cookies, executionDuration, userAgent, browserLanguage);
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
			log.error("Login flow failed: {}", flowException.getMessage(), flowException);
			throw new CouponBoosterException("Login flow failed: " + flowException.getMessage(), flowException);
		}
	}

	private void navigateToLoginPage(Page page) {
		if (log.isDebugEnabled()) {
			log.debug("Navigating to: {}", browserConfiguration.loginUrl());
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
				log.debug("Accepted cookies");
			}
		}
		catch (TimeoutError e) {
			log.error("Cookie consent dialog not found or not needed, continuing: {}", e.getMessage(), e);
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
		field.focus();
		field.clear();
		if (browserConfiguration.typingDelayMs() > 0) {
			field.pressSequentially(text,
					new Locator.PressSequentiallyOptions().setDelay(browserConfiguration.typingDelayMs()));
		}
		else {
			field.fill(text); // fallback: fast input if no delay is needed
		}
	}

	private void clickElement(Page page, String selector) {
		findElement(page, selector).click();
	}

	private Locator findElement(Page page, String selector) {
		try {
			var element = page.locator(selector);
			element.first().waitFor(new Locator.WaitForOptions().setTimeout(20000));

			if (!element.first().isVisible()) {
				throw new CouponBoosterException("Element not visible: " + selector);
			}

			return element;
		}
		catch (TimeoutError e) {
			throw new CouponBoosterException("Element not found: " + selector, e);
		}
	}

}
