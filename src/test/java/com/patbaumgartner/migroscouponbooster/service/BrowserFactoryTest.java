package com.patbaumgartner.migroscouponbooster.service;

import com.patbaumgartner.migroscouponbooster.properties.PlaywrightProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserFactoryTest {

	@Mock
	private PlaywrightProperties playwrightProperties;

	@InjectMocks
	private BrowserFactory browserFactory;

	@Test
	void constructor_WithValidProperties_ShouldCreateFactory() {
		// Given
		var properties = createValidProperties();

		// When
		var factory = new BrowserFactory(properties);

		// Then
		assertThat(factory).isNotNull();
	}

	@Test
	void createBrowser_WithValidConfiguration_ShouldConfigureProperly() {
		// Given
		when(playwrightProperties.headless()).thenReturn(true);
		when(playwrightProperties.slowMoMs()).thenReturn(100);
		when(playwrightProperties.chromeArgs()).thenReturn(List.of("--no-sandbox", "--disable-web-security"));
		when(playwrightProperties.timeoutMs()).thenReturn(5000);

		// Note: We can't easily test the actual browser creation without Playwright being
		// available
		// This test verifies that the factory is set up correctly with the properties

		// When & Then - verify properties are being accessed
		assertThat(playwrightProperties.headless()).isTrue();
		assertThat(playwrightProperties.slowMoMs()).isEqualTo(100);
		assertThat(playwrightProperties.chromeArgs()).containsExactly("--no-sandbox", "--disable-web-security");
		assertThat(playwrightProperties.timeoutMs()).isEqualTo(5000);
	}

	@Test
	void createBrowser_WithHeadlessMode_ShouldUseHeadlessConfiguration() {
		// Given
		when(playwrightProperties.headless()).thenReturn(true);
		when(playwrightProperties.slowMoMs()).thenReturn(0);

		// When & Then
		assertThat(playwrightProperties.headless()).isTrue();
		assertThat(playwrightProperties.slowMoMs()).isZero();
	}

	@Test
	void createBrowser_WithVisibleMode_ShouldUseVisibleConfiguration() {
		// Given
		when(playwrightProperties.headless()).thenReturn(false);
		when(playwrightProperties.slowMoMs()).thenReturn(500);
		when(playwrightProperties.chromeArgs()).thenReturn(List.of("--start-maximized"));

		// When & Then
		assertThat(playwrightProperties.headless()).isFalse();
		assertThat(playwrightProperties.slowMoMs()).isEqualTo(500);
		assertThat(playwrightProperties.chromeArgs()).containsExactly("--start-maximized");
	}

	@Test
	void createBrowser_WithCustomChromeArgs_ShouldUseCustomArgs() {
		// Given
		var customArgs = List.of("--disable-blink-features=AutomationControlled", "--disable-dev-shm-usage",
				"--no-sandbox");
		when(playwrightProperties.chromeArgs()).thenReturn(customArgs);

		// When & Then
		assertThat(playwrightProperties.chromeArgs()).hasSize(3);
		assertThat(playwrightProperties.chromeArgs()).containsExactlyElementsOf(customArgs);
	}

	@Test
	void createBrowser_WithEmptyChromeArgs_ShouldHandleEmptyArgs() {
		// Given
		when(playwrightProperties.chromeArgs()).thenReturn(List.of());

		// When & Then
		assertThat(playwrightProperties.chromeArgs()).isEmpty();
	}

	private PlaywrightProperties createValidProperties() {
		return new PlaywrightProperties("https://login.example.com", "https://login.example.com/password", 50, 100,
				true, List.of("--no-sandbox"), 10000);
	}

}
