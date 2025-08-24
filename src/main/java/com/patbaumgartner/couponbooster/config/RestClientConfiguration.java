package com.patbaumgartner.couponbooster.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.CONNECTION;

@Configuration
public class RestClientConfiguration {

	private static final Logger log = LoggerFactory.getLogger(RestClientConfiguration.class);

	@Bean
	RestClient.Builder restClientBuilder() {
		log.debug("Configuring REST client builder with request logging interceptor and default headers");
		return RestClient.builder().requestInterceptor(createRequestLoggingInterceptor());
	}

	@Bean
	RestClientCustomizer restClientCustomizer() {
		log.debug("Configuring REST client customizer with compression and connection headers");
		return restClientBuilder -> restClientBuilder.defaultHeader(ACCEPT_ENCODING, "gzip, deflate, br")
			.defaultHeader(CONNECTION, "keep-alive");
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
