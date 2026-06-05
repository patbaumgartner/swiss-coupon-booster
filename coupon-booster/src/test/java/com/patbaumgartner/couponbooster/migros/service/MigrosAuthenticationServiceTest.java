package com.patbaumgartner.couponbooster.migros.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.patbaumgartner.couponbooster.migros.properties.MigrosPlaywrightProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosSelectorsProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import com.patbaumgartner.couponbooster.service.PlaywrightProvider;
import com.patbaumgartner.couponbooster.util.WebAuthnDisabler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigrosAuthenticationServiceTest {

	private MigrosUserProperties userCredentials;

	private MigrosPlaywrightProperties browserConfiguration;

	private MigrosSelectorsProperties elementSelectors;

	@Mock
	private MigrosBrowserFactory browserCreator;

	@Mock
	private WebAuthnDisabler webAuthnDisabler;

	@Mock
	private PlaywrightProvider playwrightProvider;

	@Mock
	private Playwright playwright;

	@Mock
	private Browser browser;

	@Mock
	private BrowserContext browserContext;

	@Mock
	private Page page;

	@Mock
	private Locator locator;

	private MigrosAuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		userCredentials = new MigrosUserProperties("test@example.com", "s3cr3t");
		browserConfiguration = new MigrosPlaywrightProperties("https://login.migros.ch", "https://login.migros.ch/pw",
				50, 0, true, List.of(), 5000);
		elementSelectors = new MigrosSelectorsProperties("#email", "#password", "#submit", "#pwLogin", "#cookieOk");

		when(playwrightProvider.create()).thenReturn(playwright);
		when(browserCreator.createBrowser(playwright)).thenReturn(browser);
		when(browser.newContext()).thenReturn(browserContext);
		when(browserContext.newPage()).thenReturn(page);

		when(page.evaluate("() => navigator.userAgent")).thenReturn("TestUserAgent");
		when(page.evaluate("() => navigator.language")).thenReturn("de-CH");
		when(page.url()).thenReturn("https://www.migros.ch/home");

		when(page.locator(anyString())).thenReturn(locator);
		when(locator.first()).thenReturn(locator);
		when(locator.isVisible()).thenReturn(true);

		when(webAuthnDisabler.getDisableScript()).thenReturn("/* disable webauthn */");
		when(browserContext.cookies()).thenReturn(Collections.emptyList());

		authenticationService = new MigrosAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, webAuthnDisabler, playwrightProvider);
	}

	@Test
	void performAuthentication_withValidCredentials_returnsSuccessfulResult() {
		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isTrue();
		assertThat(result.userAgent()).isEqualTo("TestUserAgent");
		assertThat(result.browserLanguage()).isEqualTo("de-CH");
	}

	@Test
	void performAuthentication_withMissingEmail_returnsFailedResult() {
		userCredentials = new MigrosUserProperties("user@example.com", "  ");
		authenticationService = new MigrosAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, webAuthnDisabler, playwrightProvider);

		// Credentials are validated before Playwright is initialised
		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("credentials");
		verify(playwrightProvider, never()).create();
	}

	@Test
	void performAuthentication_withMissingPassword_returnsFailedResult() {
		userCredentials = new MigrosUserProperties("user@example.com", "");
		authenticationService = new MigrosAuthenticationService(userCredentials, browserConfiguration, elementSelectors,
				browserCreator, webAuthnDisabler, playwrightProvider);

		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("credentials");
		verify(playwrightProvider, never()).create();
	}

	@Test
	void performAuthentication_withSuccessfulLogin_returnsCookies() {
		var cookie = new com.microsoft.playwright.options.Cookie("session", "abc123");
		when(browserContext.cookies()).thenReturn(List.of(cookie));

		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isTrue();
		assertThat(result.sessionCookies()).hasSize(1);
		assertThat(result.sessionCookies().get(0).name).isEqualTo("session");
	}

	@Test
	void performAuthentication_whenStillOnLoginPage_returnsFailedResult() {
		when(page.url()).thenReturn("https://login.migros.ch/auth");

		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("Authentication flow failed");
	}

	@Test
	void performAuthentication_whenBrowserThrows_returnsFailedResult() {
		when(browserCreator.createBrowser(any())).thenThrow(new RuntimeException("browser crash"));

		AuthenticationResult result = authenticationService.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("browser crash");
	}

}
