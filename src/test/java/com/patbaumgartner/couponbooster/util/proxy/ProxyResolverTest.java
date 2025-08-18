package com.patbaumgartner.couponbooster.util.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyResolverTest {

	@Mock
	private RestClient restClient;

	@Mock
	private RestClient.Builder restClientBuilder;

	@Mock
	private ProxyProperties proxyProperties;

	private ProxyResolver proxyResolver;

	@BeforeEach
	void setUp() {
		when(restClientBuilder.build()).thenReturn(restClient);
		proxyResolver = new ProxyResolver(restClientBuilder, proxyProperties);
	}

	@Test
	void fetchProxyList_shouldReturnProxyList() {
		// Given
		when(proxyProperties.listUrl()).thenReturn("https://example.com/proxies.txt");

		RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
		RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
		RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

		when(restClient.get()).thenReturn(uriSpec);
		when(uriSpec.uri(anyString())).thenReturn(headersSpec);
		when(headersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class)).thenReturn("host1:8080:user1:pass1\nhost2:8081:user2:pass2");

		// When
		List<ProxyAddress> proxyList = proxyResolver.fetchProxyList();

		// Then
		assertThat(proxyList).hasSize(2);
		assertThat(proxyList.get(0).host()).isEqualTo("host1");
		assertThat(proxyList.get(1).host()).isEqualTo("host2");
	}

	@Test
	void getNextProxy_shouldReturnProxiesInOrder() {
		// Given
		when(proxyProperties.listUrl()).thenReturn("https://example.com/proxies.txt");

		RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
		RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
		RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

		when(restClient.get()).thenReturn(uriSpec);
		when(uriSpec.uri(anyString())).thenReturn(headersSpec);
		when(headersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class))
			.thenReturn("host1:8080:user1:pass1\nhost2:8081:user2:pass2\nhost3:8081:user3:pass3");

		// When
		ProxyAddress proxy1 = proxyResolver.getNextProxy();
		ProxyAddress proxy2 = proxyResolver.getNextProxy();

		// Then
		assertThat(proxy1.host()).isEqualTo("host2");
		assertThat(proxy2.host()).isEqualTo("host3");
	}

	@Test
	void getCurrentProxy_shouldReturnRandomProxy() {
		// Given
		when(proxyProperties.listUrl()).thenReturn("https://example.com/proxies.txt");

		RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
		RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
		RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

		when(restClient.get()).thenReturn(uriSpec);
		when(uriSpec.uri(anyString())).thenReturn(headersSpec);
		when(headersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class)).thenReturn("host1:8080:user1:pass1\nhost2:8081:user2:pass2");

		// When
		ProxyAddress proxy = proxyResolver.getCurrentProxy();

		// Then
		assertThat(proxy).isNotNull();
		assertThat(proxy.host()).isIn("host1", "host2");
	}

}
