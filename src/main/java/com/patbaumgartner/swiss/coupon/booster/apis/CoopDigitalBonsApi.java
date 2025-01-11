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

	private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9,de-CH;q=0.8,de-DE;q=0.7,de;q=0.6";

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
				.secure()
				.followRedirect(true)
				.wiretap(Transport.class.getCanonicalName(), LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)))
			.defaultHeader("User-Agent", USER_AGENT)
			.defaultHeader("Accept-Language", ACCEPT_LANGUAGE)
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
			.uri(supercardSettings.startUrl())
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
		String body = "username=" + supercardSettings.username() + "&password=" + supercardSettings.password()
				+ "&rememberMe=true" + "&execution=" + execution
				+ "&_eventId=submit&geolocation=&g-recaptcha-response=03AFcWeA6LGuiFJYc3GaWPmTHyhpOjw_ubq0TA8qqJFUg6AC6sL7IJdFE_0pNTIZv0VYVHg9spQd7JR5F4GtyJ9fKQLmdnfg5d1-2R9LegUkf24AZ7KhMuPqeAjmrNJo92p-rY82XrxuNStUltIg3WVBPE34k9kfSKW4VE9JSZ0QWp3UZn1E3y99z-RDQRmrfqKKao-Y8-R_6-Prxl0w7oyj6iYu335PKOTksQnqmv3M_B0N06EDvSZygos4Oh1L1gOadBnKLWbFC-m_zD5abO1NzyVY7ljtAdjK8BRclXAARjJCSSrgUt2VMFjUNjI_SYdFPZvdcWiZYa7JKgFl3ls-e8X_gAWs5s7Zz3cwkNCLJDIXXFcnujWClYVl-5exIJiTURin_C0ZSFN8e-g36OxdVhv3UD_UdXSzDhlgYdHYfS5aX0x0AjqYqXP--8KqzCCfTZq6FxoFtPFeEHgWsZFHCunpvqSt602n40mXlfi46dSsSnDICQPS4qUD1r_dOioiWd4ihZ3A5mXMx11EdLt-FSVaE93_cwup36_Fp5gd_CRlMy-0uqeVj4L29WpBNyPhyX8_-xurg3zRzU9Y7H58QQ1HcJyjW6ZSGQKJE7Ud8Jhn_BkTAUvauin5UuY7EFgS9RxgVD_Na0z25qZEF4U221gClOHozXx35_vnRtDhtppyhZDoszXZfPjPPV1YY9SObrEcYBJoMZmhAr9cStCtMJc4TA2XRLQ7Vyw95OH7Moz9ZYlkYc-LBud3T7DBaxaB3kQPIMGK4EEJ7bNQAf2Mor6H7vAIF9p0DyglWRnCwy4Wj8Kyq5X4242COJijdj5VyNmZeZb79iutgMCnbGcFr98tPwDeTI00H0WtXEYNmpCF4cis4fzhS1-rVk-kHOu7y_TMEMN1hNIb1rno1d-WnUdKfY8PggZc36c5-pzagxBZOcphJ-SKCRzQ84j5d8p080itmHBQsERKJw2vyHLqwcMyb3tYZdxUmQPM9emndr-bapBf_srA9A0DYtksvXkTSKKAW9kgYeMclCkWMUlIVx-skr1TPrkH-sohbLbB0LXSwIv3W8JOMkAglLzJcMvXHDKplxpjhHp7B8FhUq18EyYCdM3r6BagtIpjlMbpPkV3t5lY7KvJ607UY_kL4WGu5FSmj7ntI2ihy1Imojqj_9gPuwxZ7WjZC3M9BTkiFLjLWjQUjcEwgK97OiZ0pGhoxa0lhVvRqsdaAas-3DfC7c84YyEGtqOxvcm5lp6U4fCFmF4M3qc7r13RinMjXtrcJlLxCH7t4mKyNygiz8DeN7hymXbnBCRoTpWHK4hM9Yub_mQjnTAumoH7EKViVG2ixGBVt5POOJ0rhkwDROqoYn_nRRlxL5fqyDVgxX7YvCxWnzCfamvo-iViyMG1ykM-SRWDH1TSVsQTl_kPjQNlOKQ2-dVfE5C5u00zrhBvT2ZwqGzQdVApbQFDYTeJgQclYyCB0jXCrLJh4n8LngUQVhlvs_gTFAp06OiXp2YNQgZfSp-iqn6siJyXv6dSZAua7fE-1ILshGZyaDeAHriUCAdA5Y7k5VPetN6L49tGqdIdqiNG3IjnYcIAFbt_kXKDJubdJNyWePU5bpFRkuZYFrTGQ4FKEFyS3wtIZyd9OVlOlC9buI1tdAl3aYE1GNrNW01oNNI4B_hgBprWL_nTuFyfHuQuZxvvHODPrkh9f3617jgIJaciSj22n8vgLzAKF0VuoDOfTtKsImPNScXwegf-ZtL-5uCwkcPwKZrgKFH0cfxU-UKlDcAJIdtQtSws-wLPks9jfMEQVTa14pYC8TzUhUUCro93DHLuoLthWkI_eOkzMOg1EYCZgSGHsEwxJCxUJEXQATT5ZykDgGO0nHmi3t_8THbHitHJfCv0GkfHqgbpSzRaI2elPwsxR_aHiD_5MDEgXr";

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
			.header("Accept-Language", ACCEPT_LANGUAGE)
			.cookie("CMSSESSIONID", supercardSettings.cmsCookie())
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
