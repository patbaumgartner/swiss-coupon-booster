package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.patbaumgartner.couponbooster.coop.properties.CoopPlaywrightProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopSelectorsProperties;
import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoopAuthenticationServiceTest {

	@Mock
	private CoopUserProperties userCredentials;

	@Mock
	private CoopPlaywrightProperties browserConfiguration;

	@Mock
	private CoopSelectorsProperties elementSelectors;

	@Mock
	private CoopBrowserFactory browserCreator;

	@InjectMocks
	private CoopAuthenticationService coopAuthenticationService;

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

	@Test
	void performAuthentication_shouldReturnSuccessfulResult_whenLoginSucceeds() {
		try (MockedStatic<Playwright> playwrightMockedStatic = mockStatic(Playwright.class)) {
			// Arrange
			playwrightMockedStatic.when(Playwright::create).thenReturn(playwright);
			when(userCredentials.email()).thenReturn("test@example.com");
			when(userCredentials.password()).thenReturn("password");
			when(browserCreator.createBrowser(any(Playwright.class))).thenReturn(browser);
			when(browser.newContext()).thenReturn(browserContext);
			when(browserContext.newPage()).thenReturn(page);
			when(browserConfiguration.loginUrl()).thenReturn("http://dummy-login-url");
			when(elementSelectors.cookieAcceptButton()).thenReturn("#accept");
			when(elementSelectors.loginLink()).thenReturn("#login");
			when(elementSelectors.usernameInput()).thenReturn("#username");
			when(elementSelectors.passwordInput()).thenReturn("#password");
			when(elementSelectors.submitButton()).thenReturn("#submit");

			when(page.locator(anyString())).thenReturn(locator);
			when(locator.first()).thenReturn(locator);
			when(locator.isVisible()).thenReturn(true);
			when(page.navigate(anyString())).thenReturn(null);
			doNothing().when(page).waitForLoadState(any(LoadState.class));
			doNothing().when(locator).waitFor(any());
			doNothing().when(locator).click();
			doNothing().when(locator).clear();
			doNothing().when(locator).type(anyString(), any());

			Cookie sessionCookie = new Cookie("session", "dummy-session-id");
			when(browserContext.cookies()).thenReturn(Collections.singletonList(sessionCookie));

			// Act
			AuthenticationResult result = coopAuthenticationService.performAuthentication();

			// Assert
			assertThat(result.isSuccessful()).isTrue();
			assertThat(result.sessionCookies()).hasSize(1);
			assertThat(result.sessionCookies().get(0).name).isEqualTo("session");
		}
	}

	@Test
	void performAuthentication_shouldReturnFailedResult_whenCredentialsAreMissing() {
		// Arrange
		when(userCredentials.email()).thenReturn(""); // Blank email

		// Act
		AuthenticationResult result = coopAuthenticationService.performAuthentication();

		// Assert
		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("User credentials are missing");
	}

	@Test
	void performAuthentication_shouldReturnFailedResult_whenLoginFlowFails() {
		try (MockedStatic<Playwright> playwrightMockedStatic = mockStatic(Playwright.class)) {
			// Arrange
			playwrightMockedStatic.when(Playwright::create).thenReturn(playwright);
			when(userCredentials.email()).thenReturn("test@example.com");
			when(userCredentials.password()).thenReturn("password");
			when(browserCreator.createBrowser(any(Playwright.class))).thenReturn(browser);
			when(browser.newContext()).thenReturn(browserContext);
			when(browserContext.newPage()).thenReturn(page);
			when(browserConfiguration.loginUrl()).thenReturn("http://dummy-login-url");

			doThrow(new RuntimeException("Navigation failed")).when(page).navigate(anyString());

			// Act
			AuthenticationResult result = coopAuthenticationService.performAuthentication();

			// Assert
			assertThat(result.isSuccessful()).isFalse();
			assertThat(result.statusMessage()).contains("Login flow failed: Navigation failed");
		}
	}

}
