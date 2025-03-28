package com.patbaumgartner.swiss.coupon.booster.apis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.settings.CoopSupercardSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.SwissCouponBoosterSettings;

@Slf4j
public class CoopDigitalBonsApi {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";

	private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9,de-CH;q=0.8,de-DE;q=0.7,de;q=0.6";

	private String cmsCookie;

	private String datadomeCookie;

	private String webapiBearerToken;

	private CoopSupercardSettings supercardSettings;

	private SwissCouponBoosterSettings swissCouponBoosterSettings;

	private ObjectMapper objectMapper;

	private RestClient restClient;

	MultiValueMap<String, String> myCookies = new LinkedMultiValueMap<>();

	public CoopDigitalBonsApi(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			SwissCouponBoosterSettings swissCouponBoosterSettings, CoopSupercardSettings supercardSettings) {
		this.swissCouponBoosterSettings = swissCouponBoosterSettings;
		this.supercardSettings = supercardSettings;

		objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		this.objectMapper = objectMapper;

		this.restClient = restClientBuilder
			.requestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.custom()
				.setUserAgent(USER_AGENT)
				.disableCookieManagement()
				.disableContentCompression()
				.build()))
			.build();
	}

	@SneakyThrows
	public void loginSupercardAccount() {

		// Initialize the ChromeDriver
		ChromeOptions options = new ChromeOptions();
		if (swissCouponBoosterSettings.headlessEnabled()) {
			options.addArguments("--headless=new"); // Optional: run in headless mode
		}
		ChromeDriver driver = new ChromeDriver(options);

		try {
			// Open start URL
			driver.get(supercardSettings.startUrl());

			Cookie dataDomeCookie = new Cookie.Builder("datadome", supercardSettings.datadomeCookie())
				.domain(".supercard.ch")
				.path("/")
				.isSecure(true)
				.isHttpOnly(true)
				.sameSite("Lax")
				.build();

			driver.manage().addCookie(dataDomeCookie);

			// Refresh the page to apply the cookie
			driver.get(supercardSettings.loginUrl());

			driver.findElement(By.id("email")).click();
			driver.findElement(By.id("email")).sendKeys(supercardSettings.username());

			driver.findElement(By.id("password")).click();
			driver.findElement(By.id("password")).sendKeys(supercardSettings.password());

			driver.findElement(By.cssSelector(".card")).click();
			driver.findElement(By.name("submitBtn")).click();

			// Wait for the page to load and handle redirects
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body[data-context='coop-sc']")));
			log.info("URL after redirects and wait: {}", driver.getCurrentUrl());

			driver.get("https://www.supercard.ch/content/supercard/de.nocache.html?scid-oidc-login=1");

			driver.get("https://www.supercard.ch/content/supercard/de/supercard-digital/digitale-bons.html");

			if (swissCouponBoosterSettings.debugCookies()) {
				debugCookies(driver);
			}

			datadomeCookie = extractCookieByName(driver, "datadome");

			cmsCookie = extractCookieByName(driver, "CMSSESSIONID");
			if (StringUtils.hasLength(cmsCookie)) {
				log.info("Successfully logged into Supercard account.");
			}
		}
		finally {
			// Close the browser after the task is done
			driver.quit();
		}
	}

	private String extractCookieByName(WebDriver driver, String cookieName) {
		Assert.notNull(driver, "Driver cannot be null!");
		Assert.notNull(cookieName, "cookieName cannot be null!");
		Assert.isTrue(!cookieName.isEmpty(), "cookieName cannot be empty!");

		Set<Cookie> cookies = driver.manage().getCookies();
		for (Cookie cookie : cookies) {
			if (cookieName.equals(cookie.getName())) {
				return cookie.getValue();
			}
			;
		}
		return null;
	}

	private void debugCookies(WebDriver driver) {
		Assert.notNull(driver, "Driver cannot be null!");

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

	@SneakyThrows
	public void extractJwtTokenFromPwaConfigs() {

		ResponseEntity<String> configResponse = restClient.get()
			.uri(supercardSettings.configUrl())
			.accept(MediaType.APPLICATION_JSON)
			.header("Accept-Language", ACCEPT_LANGUAGE)
			.cookie("CMSSESSIONID", cmsCookie)
			.cookie("datadome", datadomeCookie)
			.header("Referer", supercardSettings.configUrlReferer())
			.retrieve()
			.toEntity(String.class);

		if (configResponse.getStatusCode() != HttpStatus.OK
				|| configResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CoopDigitalBonsApiException("JWT token extraction failed.");
		}

		JsonNode rootNode = objectMapper.readTree(configResponse.getBody());
		JsonNode jwtToken = rootNode.path("jwtToken");
		this.webapiBearerToken = jwtToken.asText();
	}

	public record SupercardDigitalBon(String code, String status, String shop, boolean isNew, boolean isRecommendation,
			boolean hasLogoProduct, List<String> productTypes, LocalDateTime endDate) {
	}

	@SneakyThrows
	public List<SupercardDigitalBon> collectSupercardDigitalBons() {

		ResponseEntity<String> collectionResponse = restClient.get()
			.uri(supercardSettings.couponsUrl())
			.accept(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + webapiBearerToken)
			.header("X-Client-Id", "WEB_SUPERCARD")
			.retrieve()
			.toEntity(String.class);

		if (collectionResponse.getStatusCode() != HttpStatus.OK
				|| collectionResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CoopDigitalBonsApiException("Digital bons activation failed.");
		}

		List<SupercardDigitalBon> digitalBonCollection = new ArrayList<>();

		JsonNode rootNode = objectMapper.readTree(collectionResponse.getBody());
		JsonNode digitalBons = rootNode.path("dc");

		if (digitalBons.isArray()) {
			for (JsonNode bon : digitalBons) {
				String code = bon.path("code").asText();
				String status = bon.path("status").asText();
				String shop = bon.path("formatIdMain").asText();
				boolean isNew = bon.path("isNew").asBoolean();
				boolean isRecommendation = bon.path("isRecommendation").asBoolean();
				boolean hasLogoProduct = !bon.path("logoProduct").asText("none").equals("none");

				List<String> productTypes = new ArrayList<>();
				JsonNode productTypeArray = bon.path("productTypes");
				if (productTypeArray.isArray()) {
					for (JsonNode productType : productTypeArray) {
						productTypes.add(productType.asText());
					}
				}

				LocalDateTime endDate = LocalDateTime.parse(bon.path("endDate").asText(),
						DateTimeFormatter.ISO_DATE_TIME);

				digitalBonCollection.add(new SupercardDigitalBon(code, status, shop, isNew, isRecommendation,
						hasLogoProduct, productTypes, endDate));
				log.info("Found digital coupon: {} - {}", code, status);
			}
		}

		return digitalBonCollection;
	}

	public record SupercardDigitalBonsCollection(List<String> codes) {
	}

	@SneakyThrows
	public void deactivateSupercardDigitalBons(List<SupercardDigitalBon> activeDigitalBons) {

		List<String> openIdCodes = activeDigitalBons.stream().map(item -> item.code()).toList();

		ResponseEntity<String> activationResponse = restClient.put()
			.uri(supercardSettings.couponsDeactivationUrl())
			.accept(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + webapiBearerToken)
			.header("X-Client-Id", "WEB_SUPERCARD")
			.contentType(MediaType.APPLICATION_JSON)
			.body(objectMapper.writeValueAsString(new SupercardDigitalBonsCollection(openIdCodes)))
			.retrieve()
			.toEntity(String.class);

		if (activationResponse.getStatusCode() != HttpStatus.OK
				|| activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CoopDigitalBonsApiException("Digital bons de-activation failed.");
		}
	}

	@SneakyThrows
	public void activateSupercardDigitalBons(List<SupercardDigitalBon> inactiveDigitalBons) {

		List<String> openIdCodes = inactiveDigitalBons.stream().map(item -> item.code()).toList();

		ResponseEntity<String> activationResponse = restClient.put()
			.uri(supercardSettings.couponsActivationUrl())
			.accept(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + webapiBearerToken)
			.header("X-Client-Id", "WEB_SUPERCARD")
			.contentType(MediaType.APPLICATION_JSON)
			.body(objectMapper.writeValueAsString(new SupercardDigitalBonsCollection(openIdCodes)))
			.retrieve()
			.toEntity(String.class);

		if (activationResponse.getStatusCode() != HttpStatus.OK
				|| activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CoopDigitalBonsApiException("Digital bons activation failed.");
		}
	}

}
