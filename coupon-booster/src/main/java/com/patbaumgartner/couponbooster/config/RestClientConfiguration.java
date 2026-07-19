package com.patbaumgartner.couponbooster.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.CONNECTION;

/**
 * Configures the default {@link RestClient} with compression headers, connect/read
 * timeouts, and a request logging interceptor.
 * <p>
 * The read timeout must exceed the stealth sidecar's worst-case login time. A cold Coop
 * login can take several minutes (slow SSO redirect plus a DataDome challenge, navigation
 * retries with backoff), so the default (~3 min) previously cut off logins that would
 * otherwise have succeeded. The read timeout is intentionally generous and tunable via
 * {@code couponbooster.sidecar.read-timeout}.
 * <p>
 * The Apache HttpComponents request factory is selected explicitly instead of
 * {@code ClientHttpRequestFactoryBuilder.detect()}: in the GraalVM native image the
 * classpath probe used by {@code detect()} cannot see HttpClient 5 (it is only reachable
 * when referenced directly), so detection silently fell back to the JDK HttpClient. That
 * client defaults to HTTP/2 and sends an {@code Upgrade: h2c} handshake on plain HTTP,
 * which the uvicorn sidecar answers without ever passing the request body to FastAPI —
 * every login failed with 422 "body Field required".
 */
@Configuration
public class RestClientConfiguration {

	private static final Logger log = LoggerFactory.getLogger(RestClientConfiguration.class);

	private final Duration connectTimeout;

	private final Duration readTimeout;

	RestClientConfiguration(@Value("${couponbooster.sidecar.connect-timeout:10s}") Duration connectTimeout,
			@Value("${couponbooster.sidecar.read-timeout:300s}") Duration readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Bean
	RestClientCustomizer restClientCustomizer() {
		log.debug(
				"Configuring REST client customizer (connect-timeout={}, read-timeout={}) with compression headers and request logging interceptor",
				connectTimeout, readTimeout);
		var requestFactorySettings = HttpClientSettings.defaults()
			.withConnectTimeout(connectTimeout)
			.withReadTimeout(readTimeout);
		return restClientBuilder -> restClientBuilder
			.requestFactory(ClientHttpRequestFactoryBuilder.httpComponents().build(requestFactorySettings))
			.defaultHeader(ACCEPT_ENCODING, "gzip, deflate, br")
			.defaultHeader(CONNECTION, "keep-alive")
			.requestInterceptor(createRequestLoggingInterceptor());
	}

	private ClientHttpRequestInterceptor createRequestLoggingInterceptor() {
		return (httpRequest, requestBody, requestExecution) -> {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Executing {} HTTP request to endpoint: {}", httpRequest.getMethod(),
							httpRequest.getURI());
				}

				var httpResponse = requestExecution.execute(httpRequest, requestBody);

				if (log.isDebugEnabled()) {
					log.debug("HTTP request completed successfully with status code: {}", httpResponse.getStatusCode());
				}

				return httpResponse;
			}
			catch (Exception requestException) {
				log.error("HTTP request failed for endpoint {}: {}", httpRequest.getURI(),
						requestException.getMessage(), requestException);
				throw requestException;
			}
		};
	}

}
