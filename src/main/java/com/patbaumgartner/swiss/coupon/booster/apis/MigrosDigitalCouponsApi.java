package com.patbaumgartner.swiss.coupon.booster.apis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosAccountSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosCumulusSettings;

@Slf4j
public class MigrosDigitalCouponsApi {

	public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";

	private MigrosAccountSettings accountSettings;

	private MigrosCumulusSettings cumulusSettings;

	private RestClient restClient;

	private ObjectMapper objectMapper;

	public MigrosDigitalCouponsApi(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			MigrosAccountSettings migrosAccountSettings, MigrosCumulusSettings migrosCumulusSettings) {
		this.accountSettings = migrosAccountSettings;
		this.cumulusSettings = migrosCumulusSettings;

		this.objectMapper = objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

		this.restClient = restClientBuilder
			.requestFactory(
					new HttpComponentsClientHttpRequestFactory(HttpClients.custom().setUserAgent(USER_AGENT).build()))
			.build();
	}

	public void loginMigrosAccount() {
		// Step 1: Call to login into Migros Account

		// Step 1.1: Get CSRF token by sending a GET request
		String responseBody = restClient.get()
			.uri(accountSettings.loginUrl())
			.accept(MediaType.TEXT_HTML)
			.retrieve()
			.body(String.class);

		// Step 1.2: Extract CSRF token
		Document document = Jsoup.parse(responseBody);
		Elements metaElements = document.select("meta[name=_csrf]");

		String csrfToken = null;
		if (!metaElements.isEmpty()) {
			Element metaElement = metaElements.first();
			csrfToken = metaElement.attr("content");
		}
		else {
			throw new MigrosDigitalCouponsApiException("CSRF token not found.");
		}

		// Step 1.3: Authenticate using the CSRF token, username, and password
		String body = "_csrf=" + csrfToken + "&username=" + accountSettings.username() + "&password="
				+ accountSettings.password();

		ResponseEntity<String> loginResponse = restClient.post()
			.uri(accountSettings.loginUrl())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(body)
			.retrieve()
			.toEntity(String.class);

		if (loginResponse.getStatusCode() != HttpStatus.OK) {
			throw new MigrosDigitalCouponsApiException("Authentication failed. Please check your credentials.");
		}

		log.info("Successfully logged into Migros account.");

	}

	public void loginCumulus() {
		// Step 2: Send GET request to login to Cumulus after successful authentication
		ResponseEntity<String> cumulusResponse = restClient.get()
			.uri(cumulusSettings.loginUrl())
			.accept(MediaType.TEXT_HTML)
			.retrieve()
			.toEntity(String.class);

		if (cumulusResponse.getStatusCode() != HttpStatus.OK) {
			throw new MigrosDigitalCouponsApiException("Cumulus login failed.");
		}

		log.info("Successfully logged into Cumulus account.");
	}

	public record CumulusDigitalCoupon(String id, String name) {
	}

	@SneakyThrows
	public List<CumulusDigitalCoupon> collectCumulusDigitalCoupons() {
		// Step 3: Send GET request to collect Cumulus Digital Coupons

		ResponseEntity<String> collectResponse = restClient.get()
			.uri(cumulusSettings.couponsUrl())
			.accept(MediaType.TEXT_HTML)
			.retrieve()
			.toEntity(String.class);

		if (collectResponse.getStatusCode() != HttpStatus.OK) {
			throw new MigrosDigitalCouponsApiException("Digital coupons collection failed.");
		}

		// Step 3.1: Extract digital bons from the response
		List<CumulusDigitalCoupon> digitalCoupons = new ArrayList<>();
		Document document = Jsoup.parse(collectResponse.getBody());

		// Select all <article> elements
		for (Element article : document.select("article")) {
			// Extract the value of the data-setup attribute
			String dataSetup = article.attr("data-setup");

			// Parse the JSON part of the data-setup using Jackson
			JsonNode rootNode = objectMapper.readTree(dataSetup);
			JsonNode dataNode = rootNode.path("data");
			JsonNode promotionsNode = dataNode.path("promotions");

			// If there are promotions in the array, extract the details
			if (promotionsNode.isArray()) {
				for (JsonNode promotion : promotionsNode) {
					String id = promotion.path("id").asText();
					String name = promotion.path("name").asText();

					digitalCoupons.add(new CumulusDigitalCoupon(id, name));
					log.info("Found digital coupon: {} - {}", id, name);
				}
			}
		}

		log.info("Successfully collected {} ditigal coupons.", digitalCoupons.size());

		return digitalCoupons;
	}

	public void activateCumulusDigitalCoupons(List<CumulusDigitalCoupon> coupons) {
		// Step 4: Send POST request to activate the digital coupon

		coupons.forEach(coupon -> {

			ResponseEntity<String> activationResponse = restClient.post()
				.uri(cumulusSettings.couponsActivationUrl(), coupon.id())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.toEntity(String.class);

			if (activationResponse.getStatusCode() != HttpStatus.OK) {
				throw new MigrosDigitalCouponsApiException("Activation failed.");
			}

		});
	}

}
