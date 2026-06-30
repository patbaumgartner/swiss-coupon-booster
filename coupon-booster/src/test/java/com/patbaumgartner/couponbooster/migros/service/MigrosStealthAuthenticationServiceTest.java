package com.patbaumgartner.couponbooster.migros.service;

import com.patbaumgartner.couponbooster.migros.properties.MigrosPatchrightProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.model.AuthenticationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(MigrosStealthAuthenticationService.class)
@TestPropertySource(properties = "migros.auth.mode=sidecar")
class MigrosStealthAuthenticationServiceTest {

	@Autowired
	private MigrosStealthAuthenticationService service;

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private MigrosUserProperties userCredentials;

	@MockitoBean
	private MigrosPatchrightProperties patchrightProperties;

	@BeforeEach
	void setUp() {
		when(userCredentials.email()).thenReturn("user@example.com");
		when(userCredentials.password()).thenReturn("secret");
		when(patchrightProperties.url()).thenReturn("http://patchright:8000");
	}

	@Test
	void performAuthentication_success_returnsCookiesAndMetadata() {
		String responseBody = """
				{
				  "cookies": [
				    {
				      "name": "m-session",
				      "value": "migros-session-token",
				      "domain": ".migros.ch",
				      "path": "/",
				      "expires": 9999999999.0,
				      "httpOnly": true,
				      "secure": true,
				      "sameSite": "Strict"
				    },
				    {
				      "name": "cumulus-id",
				      "value": "abc123",
				      "domain": "account.migros.ch",
				      "path": "/",
				      "expires": -1,
				      "httpOnly": false,
				      "secure": true,
				      "sameSite": "None"
				    }
				  ],
				  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120",
				  "language": "de-CH"
				}
				""";

		server.expect(requestTo("/login/migros")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		AuthenticationResult result = service.performAuthentication();

		assertThat(result.isSuccessful()).isTrue();
		assertThat(result.sessionCookies()).hasSize(2);
		assertThat(result.sessionCookies().get(0).name).isEqualTo("m-session");
		assertThat(result.sessionCookies().get(1).name).isEqualTo("cumulus-id");
		assertThat(result.userAgent()).isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120");
		assertThat(result.browserLanguage()).isEqualTo("de-CH");
		server.verify();
	}

	@Test
	void performAuthentication_sidecarError_returnsFailedResult() {
		server.expect(requestTo("/login/migros")).andRespond(withServerError());

		AuthenticationResult result = service.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("Sidecar request failed");
	}

	@Test
	void performAuthentication_missingCredentials_returnsFailedResult() {
		when(userCredentials.email()).thenReturn("");
		when(userCredentials.password()).thenReturn("");

		AuthenticationResult result = service.performAuthentication();

		assertThat(result.isSuccessful()).isFalse();
		assertThat(result.statusMessage()).contains("credentials missing");
		server.verify(); // No HTTP call should have been made
	}

	@Test
	void performAuthentication_emptyCookiesArray_returnsSuccessWithNoCookies() {
		String responseBody = """
				{
				  "cookies": [],
				  "userAgent": "Mozilla/5.0",
				  "language": "de-CH"
				}
				""";

		server.expect(requestTo("/login/migros")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		AuthenticationResult result = service.performAuthentication();

		assertThat(result.isSuccessful()).isTrue();
		assertThat(result.sessionCookies()).isEmpty();
		server.verify();
	}

}
