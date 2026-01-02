package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.PlaywrightProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
	void performAuthentication_Successful_WithoutCookies() {
		// Config without cookies file
		browserConfiguration = new CoopPlaywrightProperties("http://login.url", null, 0, 0, true, List.of(), 1000,
				null);
		authenticationService = new CoopAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, stealthInjector, playwrightProvider);

		// No existing cookies
		when(browserContext.cookies()).thenReturn(Collections.emptyList());
		when(locator.isVisible()).thenReturn(false);

		// Act
		AuthenticationResult result = authenticationService.performAuthentication();

		// Assert
		assertThat(result.isSuccessful()).isTrue();
		// clearCookies() is only called when there are existing cookies
		// Since we have no cookies, it should not be called
	}

}
