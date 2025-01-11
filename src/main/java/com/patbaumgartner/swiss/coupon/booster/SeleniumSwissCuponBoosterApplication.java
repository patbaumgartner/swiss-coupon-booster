package com.patbaumgartner.swiss.coupon.booster;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v131.network.Network;
import org.openqa.selenium.devtools.v131.network.model.Headers;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

@Slf4j
public class SeleniumSwissCuponBoosterApplication {

	public static void main(String[] args) {

		ChromeOptions options = new ChromeOptions();
		// options.addArguments("--disable-blink-features=AutomationControlled");
		// options.addArguments("--headless"); // Optional: run in headless mode

		// Initialize the ChromeDriver
		ChromeDriver driver = new ChromeDriver(options);

		try {

			// Open the login page
			driver.get("https://login.supercard.ch");

			Cookie dataDomeCookie = new Cookie.Builder("datadome",
					"2Nt_WL_WTYhK3fi1jzqTn0qJg~v9RiUvjuq3rsciUjAb7~w2cCkc3M2dRmkNA6isqwQG0hVoeogB73RF20Rb1b~QOeSbjUY5ugWddsZH_TyOL9u2a71tuC5Uo6Jak5CY")
				.domain(".supercard.ch")
				.path("/")
				.isSecure(true)
				.isHttpOnly(true)
				.sameSite("Lax")
				.build();

			driver.manage().addCookie(dataDomeCookie);

			// Refresh the page to apply the cookie
			driver.get("https://login.supercard.ch/cas/login?service=https://www.supercard.ch/");

			driver.findElement(By.id("email")).click();
			driver.findElement(By.id("email")).sendKeys("contact@carmen-baumgartner.com");

			driver.findElement(By.id("password")).click();
			driver.findElement(By.id("password")).sendKeys("Mathilda77");

			driver.findElement(By.cssSelector(".card")).click();
			driver.findElement(By.name("submitBtn")).click();

			// Wait for the page to load and handle redirects
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
			wait.until(new ExpectedCondition<Boolean>() {
				private String lastUrl = null;

				@Override
				public Boolean apply(WebDriver driver) {
					String currentUrl = driver.getCurrentUrl();
					log.info("Current URL loaded: {}", currentUrl);

					// If the URL has stopped changing, consider redirects complete
					if (lastUrl != null && lastUrl.equals(currentUrl)) {
						return true;
					}

					// Update lastUrl for the next check
					lastUrl = currentUrl;

					// Continue waiting
					return false;
				}

				@Override
				public String toString() {
					return "URL to stop changing (stabilized after redirects).";
				}
			});

			// Get the current URL after redirects
			String finalUrl = driver.getCurrentUrl();
			log.info("Final URL: {}", finalUrl);

			debugCookies(true, driver);

			// Enable DevTools
			DevTools devTools = ((ChromeDriver) driver).getDevTools();
			devTools.createSession();

			// Enable Network Interception
			devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

			// Set custom headers including Referer
			Map<String, Object> headers = new HashMap<>();
			headers.put("Referer", "https://www.supercard.ch/content/supercard/de.nocache.html?scid-oidc-login=1");
			headers.put("x-requested-with", "XMLHttpRequest");

			devTools.send(Network.setExtraHTTPHeaders(new Headers(headers)));

			// Navigate to the desired URL with the custom Referer
			driver.get("https://www.supercard.ch/bin/coop/supercard/oidc/pwa/configs.json?_=173732302967");

			// Print the page source (JSON response)
			log.error("Response: {}", driver.getPageSource());

		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			// Close the browser after the task is done
			driver.quit();
		}

	}

	private static void debugCookies(boolean enabled, WebDriver driver) {
		if (enabled == false)
			return;

		Set<Cookie> cookies = driver.manage().getCookies();

		log.info("Cookies retrieved from the site: {}", driver.getCurrentUrl());
		log.info("---------------");
		for (Cookie cookie : cookies) {
			log.info("Name: {} ", cookie.getName());
			log.info("Value: {} ", cookie.getValue());
			log.info("Domain: {} ", cookie.getDomain());
			log.info("Path: {} ", cookie.getPath());
			log.info("Expiry: {} ", cookie.getExpiry());
			log.info("Is Secure: {} ", cookie.isSecure());
			log.info("Is HTTP Only: {} ", cookie.isHttpOnly());
			log.info("---------------");
		}
	}

}
