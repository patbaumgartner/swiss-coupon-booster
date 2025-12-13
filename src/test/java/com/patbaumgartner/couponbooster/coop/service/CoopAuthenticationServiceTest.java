package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.coop.config.CoopConstants;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.PlaywrightProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoopAuthenticationServiceTest {

	private CoopUserProperties userCredentials;

	private CoopPlaywrightProperties browserConfiguration;

	private CoopSelectorsProperties elementSelectors;

	@Mock
	private CoopBrowserFactory browserCreator;

	@Mock
	private DatadomeStealthInjector stealthInjector;

	@Mock
	private PlaywrightProvider playwrightProvider;

	@Mock
	private Playwright playwright;

	@Mock
	private BrowserContextHandle contextHandle;

	@Mock
	private BrowserContext browserContext;

	@Mock
	private Page page;

	@Mock
	private Locator locator;

	private CoopAuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		// Default properties
		userCredentials = new CoopUserProperties("test@example.com", "password");
		// Create default dummy properties - specific tests will overwrite if needed by
		// creating a new service instance
		// or we can test with these defaults.
		elementSelectors = new CoopSelectorsProperties("#login", "#user", "#pass", "#submit", "#cookie");

		// Setup common mocks
		when(playwrightProvider.create()).thenReturn(playwright);
		when(browserCreator.createBrowserContext(any(), any())).thenReturn(contextHandle);
		when(contextHandle.get()).thenReturn(browserContext);
		when(browserContext.newPage()).thenReturn(page);
		when(stealthInjector.getStealthScript()).thenReturn("console.log('stealth')");

		when(page.evaluate("() => navigator.userAgent")).thenReturn("TestUserAgent");
		when(page.evaluate("() => navigator.language")).thenReturn("en-US");

		// Locator common mocks
		when(page.locator(anyString())).thenReturn(locator);
		when(locator.first()).thenReturn(locator);
	}

	@Test
	void performAuthentication_Successful_WithPersistentCookies() {
		// Config with userDataDir set
		browserConfiguration = new CoopPlaywrightProperties("http://login.url", "configured-cookie-value", 0, 0, true,
				List.of(), 1000, "/tmp/existing-profile" // userDataDir set
		);
		authenticationService = new CoopAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, stealthInjector, playwrightProvider);

		// Mock existing cookies in persistent context
		Cookie dataDomeCookie = new Cookie(CoopConstants.CookieNames.DATADOME_COOKIE, "existing-value");
		List<Cookie> existingCookies = new ArrayList<>();
		existingCookies.add(dataDomeCookie); // Existing Cookie present
		when(browserContext.cookies()).thenReturn(existingCookies);

		// Mock logic needed status
		when(locator.isVisible()).thenReturn(false); // No login needed

		// Act
		AuthenticationResult result = authenticationService.performAuthentication();

		// Assert
		assertThat(result.isSuccessful()).isTrue();

		// Verify we cleared cookies and restored the existing one (part of "clean login"
		// logic)
		verify(browserContext).clearCookies();
		verify(browserContext, times(1)).addCookies(any());

		// CRITICAL: Verify we did NOT inject the configured cookie value, but used the
		// existing one (implied by flow)
		// To be sure, we can check that we didn't add a cookie with
		// "configured-cookie-value"
		ArgumentCaptor<List<Cookie>> cookieCaptor = ArgumentCaptor.forClass(List.class);
		verify(browserContext).addCookies(cookieCaptor.capture());
		List<Cookie> addedCookies = cookieCaptor.getValue();
		// Should be the existing one we restored
		assertThat(addedCookies).hasSize(1);
		assertThat(addedCookies.get(0).value).isEqualTo("existing-value");
	}

	@Test
	void performAuthentication_InjectsConfiguredCookie_WhenEnvVarSetAndNoPersistentprofile() {
		// Config WITHOUT userDataDir
		browserConfiguration = new CoopPlaywrightProperties("http://login.url", "new-configured-value", 0, 0, true,
				List.of(), 1000, null // userDataDir NOT set
		);
		authenticationService = new CoopAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, stealthInjector, playwrightProvider);

		// No existing cookies
		when(browserContext.cookies()).thenReturn(Collections.emptyList());

		when(locator.isVisible()).thenReturn(false);

		// Act
		AuthenticationResult result = authenticationService.performAuthentication();

		// Assert
		assertThat(result.isSuccessful()).isTrue();
		ArgumentCaptor<List<Cookie>> cookieCaptor = ArgumentCaptor.forClass(List.class);

		verify(browserContext).addCookies(cookieCaptor.capture());

		List<Cookie> capturedCookies = cookieCaptor.getValue();
		assertThat(capturedCookies).hasSize(1);
		assertThat(capturedCookies.get(0).name).isEqualTo(CoopConstants.CookieNames.DATADOME_COOKIE);
		assertThat(capturedCookies.get(0).value).isEqualTo("new-configured-value");
	}

}
