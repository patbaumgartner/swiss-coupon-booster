package com.patbaumgartner.swiss.coupon.booster.apis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;


import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.Transport;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.settings.CoopSupercardSettings;

import io.netty.handler.logging.LogLevel;

@Slf4j
public class CoopDigitalBonsApi {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";

	private String webapiBearerToken;

	private CoopSupercardSettings supercardSettings;

	private ObjectMapper objectMapper;

	private WebClient webClient;

	private RestClient restClient;

	MultiValueMap<String, String> myCookies = new LinkedMultiValueMap<>();

	public CoopDigitalBonsApi(WebClient.Builder webClientBuilder, RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper, CoopSupercardSettings supercardSettings) {
		this.supercardSettings = supercardSettings;

		objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		this.objectMapper = objectMapper;

		this.webClient = webClientBuilder
			.clientConnector(new ReactorClientHttpConnector(HttpClient.create()
				.protocol(HttpProtocol.H2)
				.followRedirect(true)
				.wiretap(Transport.class.getCanonicalName(), LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)))
			.defaultHeader("User-Agent", USER_AGENT)
			.defaultHeader("Accept-Language", "en-US,en;q=0.9,de-CH;q=0.8,de-DE;q=0.7,de;q=0.6")
			.defaultCookie("datadome", supercardSettings.datadomeCookie())
			.build();

		this.restClient = restClientBuilder
			.requestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.custom()
				.setUserAgent(USER_AGENT)
				.disableCookieManagement()
				.disableContentCompression()
				.build()))
			.build();
	}

	public void loginSupercardAccount() {

		// Step 1: Call to login into Supercard Account

		// Step 1.1: Get execution token by sending a GET request
		String responseBody = webClient.get()
			.uri(supercardSettings.loginUrl() + "?service=https://www.supercard.ch/")
			.accept(MediaType.TEXT_HTML)
			.exchangeToMono(response -> {
				for (Entry<String, List<ResponseCookie>> cookie : response.cookies().entrySet()) {
					myCookies.add(cookie.getKey(), cookie.getValue().getFirst().getValue());
				}
				return response.bodyToMono(String.class);
			})
			.block();

		// Step 1.2: Extract execution token
		Document document = Jsoup.parse(responseBody);
		Elements metaElements = document.select("input[name=execution]");

		String execution = null;
		if (!metaElements.isEmpty()) {
			Element metaElement = metaElements.first();
			execution = metaElement.attr("value");
		}
		else {
			throw new CoopDigitalBonsApiException("Execution token not found.");
		}

		// Step 1.3: Authenticate using the execution token, username, and password
		String body = "username=" + supercardSettings.username() + "&password=" + supercardSettings.password() +
				"&rememberMe=true" + "&execution=" + execution +
				"&_eventId=submit&geolocation=&g-recaptcha-response=03AFcWeA58UhnLwrXb2DN4VqfQiu6mEHZRYHXyX9dxDjmAt1xSRX2ZokIMgyewnERjDw6QkkzLTgT2Ie6RjHQZVP6D5y9TrXkIiQHF845UPXxVFUCT7Qt7aqNBI-aRlcQriuASlqj1OGx4tiUQhdjWtQ1uXfLCXo3Yb_rVkCrztVU9DooQpEP-yvggdjxZJP8KCxxMVveuBplx2bnhVP4YWF1d8FUPiqACOCPdR3HA7hjOOOC8dFHtmUgZ3u2DV7MCUw8XYIKACCKTzEiPw7O-YyqfzAZ-zcc9wjgF0HzSajYpz7iTD1nE6nuuSOqlUfQIg8lYV4xO4f7_LNQIh5gCxRFYjz8LfcThaxcy7yCpxtUdxwt4nNxQT8j_nJzgMwuYDt6XTUBg7bpgYABwEgKf43soPs4r4rakcv82Zig5r1yOFpgIZwwtX5ZMfbiRpCgwIKKN6QwDMf9HVuSWuEQlotNIZapqtlm3O5YFciRToAqKhlLIhb016v6Xpsf5HeRzYQF5WXY7VhH6jQKx8c10lQBlF8xN-sZ_rsfm2PX4IxAb_LsOAkwFAYQZmy5tC8u-PiZdBRD2OukXNyf4cKB07BGtJtRjjR2y0kGpWSKB0oDHllqE3WxxpawZ2SG4wDaMWbCzI1YM6sATe3hyHklEgr1qTvK5t2ZZOF3n8R6nBrUZxN6QCdPXMBPs5mhcN9Zsu7-idW-Ke3r8sXOTzWjSYay9FzlROHwrVs62gJ4yaJRMtycxffXCYbafbYCv7IjlEdKq8jPk301j92P1uDP9fMx3OZnLVUbqftILe092StacpjgoXtNcKRnAvDRYQloEMb_Jl9hnS1RuHNAZefiHdsBPQaXax0IxuFzEsFCoyLmQaLv7_oSBlgKl1D28zXu8Henh0haQqZp9BtiaxKDHklmLrUSbzcznEN49CFEYHSgxVszsKPedH8IqiL3mtPkbHhBjh8LEhd27saVWeXGAgF1YuHyogU0nF8ELK13hQveSrjhtxzDMKYEU-hqlne1wEs2hfZuXo3a-KXf0r2ce9IRZpN4zsU8WlENT2wkAPLgdCB7YPvH6OnKUiqFbPSQ8MnTDyH45Yvu51SlrzJho9toanUQGatH-eKT88McAibLTfKRHClAQrMf00VUlRTkQeUf5Th2xgFesPYXVvt3IpTddv4fNhcc1L73yrIykknSkWmYMO-V49TdbXZPZCzhwZKcI4Q8vcjSX9quXtDzgbvwllKE4VN4nGLFUyxRtR697nI1iOtpnFam6HOyGrqJM-svf0jC-jkCkQhjVLwlKX0Mztiiy1E2nhXHz-4p_C9ZVAnYSfUwt9Pwg3qgHRcsk5GE5XiGfy__eSAOKhpoHmIqAGLNSESQ1AVTGmUuErifRFs3dgjfNKJfhbaWI2bfHdXZ3u6YAI-hJ5U-4PeTBkwaB6q9GEIM7gz9jy52eyMdiQo7rMP7f8TuTvcW6SykWQDwQavHpmYxEpWW47pgDHDcVNXvVIQJp0QSESKl5bAaLmvZpFaFH2-j1gwEptR2-QfV0TmwqUj4TElMNYUz1AFVApD8FsCChah6v82dqVy_fM2FJDXFJhQ3A4bqSm-a4_hn2cI88RiJE2Rq2mZO_faRpiAjCPrYXcEN-866iEYI2_jkKwN3IxvDIk4zuAW4RXDckGXzcrNrm9aNo6eUrgSWr2qOJdIK6FWjMNz_8zUGpz5d8sTlQ1ptXp-g73TOUKah3qvCz_qIWRLciTCZvE-EFdYqkbx4ZLVJ6EhGPRRiGQ-f3VjlJjjuYyrLyc7DlioQNyxY1DMoYg6kIFqI9BGFKyluBdreHUJ-F3etZdytjD5egG0mOgG50Z32X35rNVrZ6EpDvj1OyUG6VQjozGTn67mWfSRFHFMY_EBe6EfRCcVQbvNiRKUdHBXhVIH-EqOeatZquJlAJ";

		ResponseEntity<String> loginResponse = webClient.post()
			.uri(supercardSettings.loginUrl())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.cookies(cookies -> cookies.addAll(myCookies))
			.bodyValue(body)
			.exchangeToMono(response -> {
				for (Entry<String, List<ResponseCookie>> cookie : response.cookies().entrySet()) {
					myCookies.add(cookie.getKey(), cookie.getValue().getFirst().getValue());
				}
				return response.toEntity(String.class);
			})
			.block();

		if (loginResponse.getStatusCode() != HttpStatus.OK) {
			throw new CoopDigitalBonsApiException("Authentication failed. Please check your credentials.");
		}

		log.info("Successfully logged into Supercard account.");
	}

	@SneakyThrows
	public void extractJwtTokenFromPwaConfigs() {

		ResponseEntity<String> configResponse = restClient.get()
			.uri(supercardSettings.configUrl())
			.accept(MediaType.APPLICATION_JSON)
			.header("Accept-Language", "en-US,en;q=0.9,de-CH;q=0.8,de-DE;q=0.7,de;q=0.6")
			.cookie("CMSSESSIONID", supercardSettings.cmsCookie())
			.header("Referer", supercardSettings.configUrlReferer())
			.retrieve()
			.toEntity(String.class);

		if (configResponse.getStatusCode() != HttpStatus.OK ||
				configResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
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

		if (collectionResponse.getStatusCode() != HttpStatus.OK ||
				collectionResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
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

		if (activationResponse.getStatusCode() != HttpStatus.OK ||
				activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
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

		if (activationResponse.getStatusCode() != HttpStatus.OK ||
				activationResponse.getHeaders().getContentType() == MediaType.TEXT_HTML) {
			throw new CoopDigitalBonsApiException("Digital bons activation failed.");
		}
	}

}
