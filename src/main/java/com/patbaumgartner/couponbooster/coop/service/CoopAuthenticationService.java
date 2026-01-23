package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.Geolocation;
import com.microsoft.playwright.options.LoadState;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.AbstractAuthenticationService;
import com.patbaumgartner.couponbooster.service.AuthenticationService;
import com.patbaumgartner.couponbooster.service.PlaywrightProvider;
import com.patbaumgartner.couponbooster.util.cookie.NetscapeCookieParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.CookieNames.DATADOME_COOKIE;
import static com.patbaumgartner.couponbooster.coop.config.CoopConstants.Delays.*;

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
 * <li>Extracting session cookies upon successful authentication.</li>
 * </ul>
 * It is highly configurable through properties for user credentials, Playwright settings,
 * and element selectors.
 *
 * @see CoopUserProperties
 * @see CoopPlaywrightProperties
 * @see CoopSelectorsProperties
 * @see CoopBrowserFactory
 */
@Service
public class CoopAuthenticationService extends AbstractAuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(CoopAuthenticationService.class);

	private final CoopUserProperties userCredentials;

	private final CoopPlaywrightProperties browserConfiguration;

	private final CoopSelectorsProperties elementSelectors;

	private final CoopBrowserFactory browserCreator;

	private final DatadomeStealthInjector stealthInjector;

	private final PlaywrightProvider playwrightProvider;

	/**
	 * Constructs a new {@code CoopAuthenticationService} with the specified dependencies.
	 * @param userCredentials the user's login credentials (email and password)
	 * @param browserConfiguration the configuration for the Playwright browser instance
	 * @param elementSelectors the CSS selectors for locating elements on the page
	 * @param browserCreator the factory for creating Playwright browser instances
	 * @param stealthInjector the injector for DataDome stealth scripts
	 * @param playwrightProvider the provider for Playwright instances
	 */
	public CoopAuthenticationService(CoopUserProperties userCredentials, CoopPlaywrightProperties browserConfiguration,
			CoopSelectorsProperties elementSelectors, CoopBrowserFactory browserCreator,
			DatadomeStealthInjector stealthInjector, PlaywrightProvider playwrightProvider) {
		super(); // Pass log and SECURE_RANDOM to the superclass
		this.userCredentials = Objects.requireNonNull(userCredentials, "User credentials cannot be null");
		this.browserConfiguration = Objects.requireNonNull(browserConfiguration,
				"Browser configuration cannot be null");
		this.elementSelectors = Objects.requireNonNull(elementSelectors, "Element selectors cannot be null");
		this.browserCreator = Objects.requireNonNull(browserCreator, "Browser factory cannot be null");
		this.stealthInjector = Objects.requireNonNull(stealthInjector, "Stealth injector cannot be null");
		this.playwrightProvider = Objects.requireNonNull(playwrightProvider, "Playwright provider cannot be null");
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
		try (var playwright = playwrightProvider.create()) {

			try (var contextHandle = browserCreator.createBrowserContext(playwright,
					createStealthBrowserContextOptions()); var context = contextHandle.get()) {

				// CRITICAL: Inject stealth script BEFORE creating any pages
				// This ensures the script runs before any page content loads
				context.addInitScript(stealthInjector.getStealthScript());
				log.debug("DataDome stealth script injected into browser context");

				var page = context.newPage();

				// Extract user-agent and browser language
				String userAgent = page.evaluate("() => navigator.userAgent").toString();
				String browserLanguage = page.evaluate("() => navigator.language").toString();

				if (log.isDebugEnabled()) {
					log.debug("Browser user-agent: {}", userAgent);
					log.debug("Browser language: {}", browserLanguage);
				}

				// Load cookies from file if configured (BEFORE navigation)
				loadCookiesFromFileIfConfigured(context);

				// Clean login requirement:
				// The user wants to perform a fresh login every time but keep the
				// DataDome cookie to bypass the captcha.
				var existingCookies = context.cookies();
				var datadomeCookieOpt = existingCookies.stream()
					.filter(c -> DATADOME_COOKIE.equals(c.name))
					.findFirst();

				if (!existingCookies.isEmpty()) {
					context.clearCookies();
					log.atDebug()
						.addArgument(() -> existingCookies.size())
						.log("Cleared {} existing cookies to ensure clean login");

					if (datadomeCookieOpt.isPresent()) {
						context.addCookies(Collections.singletonList(datadomeCookieOpt.get()));
						log.debug("Restored DataDome cookie from persistent storage");
					}
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

	/**
	 * Creates browser context options with stealth configurations to avoid bot detection.
	 * <p>
	 * This method configures the browser context with realistic settings including:
	 * <ul>
	 * <li>Realistic viewport size (1920x1080)</li>
	 * <li>User agent override (if needed)</li>
	 * <li>Locale and timezone settings</li>
	 * <li>Permissions for notifications, geolocation, etc.</li>
	 * </ul>
	 * @return browser context options with stealth settings
	 */
	private Browser.NewContextOptions createStealthBrowserContextOptions() {
		return new Browser.NewContextOptions().setViewportSize(1920, 1080)
			.setLocale("de-CH")
			.setTimezoneId("Europe/Zurich")
			.setPermissions(List.of("geolocation", "notifications"))
			.setGeolocation(new Geolocation(47.3769, 8.5417)) // Zurich
																// coordinates
			.setDeviceScaleFactor(1.0)
			.setIsMobile(false)
			.setHasTouch(false)
			.setColorScheme(ColorScheme.LIGHT);
	}

	private void validateUserCredentials() {
		if (isBlank(userCredentials.email()) || isBlank(userCredentials.password())) {
			throw new CouponBoosterException(
					"User credentials are missing. Configure coop.user.email and coop.user.password");
		}
	}

	private void performLoginFlow(Page page) {
		log.debug("Starting login flow");

		try {
			navigateToLoginPage(page);
			handleCookieConsent(page);

			if (isLoginNeeded(page)) {
				clickLoginLink(page);
				enterCredentialsAndSubmit(page);
				log.debug("Login flow completed");
			}
			else {
				log.info("Login link not found - assuming user is already logged in via persistent session");
			}
		}
		catch (Exception flowException) {
			log.error("Login flow failed: {}", flowException.getMessage(), flowException);
			throw new CouponBoosterException("Login flow failed: " + flowException.getMessage(), flowException);
		}
	}

	private boolean isLoginNeeded(Page page) {
		try {
			// Check for login button with a short timeout
			Locator loginButton = page.locator(elementSelectors.loginLink()).first();
			// Use a shorter timeout to check for presence
			loginButton.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			return loginButton.isVisible();
		}
		catch (TimeoutError e) {
			return false;
		}
	}

	private void navigateToLoginPage(Page page) {
		if (log.isDebugEnabled()) {
			log.debug("Navigating to: {}", browserConfiguration.loginUrl());
		}
		page.navigate(browserConfiguration.loginUrl());
		page.waitForLoadState(LoadState.NETWORKIDLE);

		// CRITICAL: Wait for DataDome's initial checks to complete
		// DataDome runs fingerprinting in the first 2-3 seconds
		addRandomDelay(DATADOME_CHECK_MIN, DATADOME_CHECK_MAX);
		log.debug("Waited for DataDome initial checks to complete");
	}

	private void handleCookieConsent(Page page) {
		try {
			// Add small random delay before interacting
			addRandomDelay(COOKIE_CONSENT_MIN, COOKIE_CONSENT_MAX);

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
		addRandomDelay(LOGIN_CLICK_MIN, LOGIN_CLICK_MAX);
		clickElement(page, elementSelectors.loginLink());
	}

	private void enterCredentialsAndSubmit(Page page) {
		addRandomDelay(INPUT_TYPING_MIN, INPUT_TYPING_MAX);
		typeIntoField(page, elementSelectors.usernameInput(), userCredentials.email(),
				browserConfiguration.typingDelayMs());

		addRandomDelay(INPUT_TYPING_MIN, INPUT_TYPING_MAX);
		typeIntoField(page, elementSelectors.passwordInput(), userCredentials.password(),
				browserConfiguration.typingDelayMs());

		addRandomDelay(SUBMIT_CLICK_MIN, SUBMIT_CLICK_MAX);
		clickElement(page, elementSelectors.submitButton());
	}

	/**
	 * Loads cookies from a file if configured in the Playwright properties. This method
	 * reads cookies from a Netscape format cookies.txt file and adds them to the browser
	 * context BEFORE any navigation occurs.
	 * <p>
	 * This is particularly useful for bypassing bot detection (DataDome) by reusing
	 * previously obtained valid cookies.
	 * @param context the browser context to add cookies to
	 */
	private void loadCookiesFromFileIfConfigured(BrowserContext context) {
		String cookiesFilePath = browserConfiguration.cookiesFilePath();

		if (cookiesFilePath == null || cookiesFilePath.isBlank()) {
			log.debug("No cookies file path configured, skipping cookie loading");
			return;
		}

		Path cookiesPath = Path.of(cookiesFilePath);
		if (!Files.exists(cookiesPath)) {
			log.warn("Cookies file does not exist: {}", cookiesFilePath);
			return;
		}

		try {
			log.info("Loading cookies from file: {}", cookiesFilePath);
			List<Cookie> cookies = NetscapeCookieParser.parseFromFile(cookiesPath);
			context.addCookies(cookies);
			log.atInfo().addArgument(() -> cookies.size()).log("Successfully loaded {} cookies from file");

			if (log.isDebugEnabled()) {
				cookies.forEach(c -> log.debug("Loaded cookie: {} = {} (domain: {})", c.name, c.value, c.domain));
			}
		}
		catch (Exception exception) {
			log.error("Failed to load cookies from file: {}", exception.getMessage(), exception);
		}
	}

}
