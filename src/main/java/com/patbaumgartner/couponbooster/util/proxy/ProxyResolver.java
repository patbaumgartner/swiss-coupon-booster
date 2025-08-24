package com.patbaumgartner.couponbooster.util.proxy;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

@Component
public class ProxyResolver {

	private final RestClient restClient;

	private final ProxyProperties proxyProperties;

	private final SecureRandom random = new SecureRandom();

	private List<ProxyAddress> currentProxyList = List.of();

	private int currentIndex = 0;

	public ProxyResolver(RestClient.Builder restClientBuilder, ProxyProperties proxyProperties) {
		this.restClient = restClientBuilder.build();
		this.proxyProperties = proxyProperties;
	}

	public synchronized ProxyAddress getNextProxy() {
		ensureProxyListAvailable();

		ProxyAddress proxy = currentProxyList.get(currentIndex);
		currentIndex++;

		return proxy;
	}

	public synchronized ProxyAddress getCurrentProxy() {
		ensureProxyListAvailable();

		if (currentProxyList.isEmpty()) {
			throw new IllegalStateException("No proxies available from the API");
		}

		// Select a random proxy from the available list
		int randomIndex = random.nextInt(currentProxyList.size());
		return currentProxyList.get(randomIndex);
	}

	public synchronized void resetIterator() {
		currentIndex = 0;
		currentProxyList = List.of(); // Force refetch on next call
	}

	public synchronized int getRemainingProxiesCount() {
		if (currentProxyList.isEmpty()) {
			return 0;
		}
		return Math.max(0, currentProxyList.size() - currentIndex);
	}

	private void ensureProxyListAvailable() {
		if (currentProxyList.isEmpty() || currentIndex >= currentProxyList.size()) {
			currentProxyList = fetchProxyList();
			currentIndex = 0;

			if (currentProxyList.isEmpty()) {
				throw new IllegalStateException("No proxies available from the API");
			}
		}
	}

	public List<ProxyAddress> fetchProxyList() {
		if (proxyProperties.listUrl() == null || proxyProperties.listUrl().isEmpty()) {
			throw new IllegalStateException("Proxy list URL is not configured");
		}

		String response = restClient.get().uri(proxyProperties.listUrl()).retrieve().body(String.class);

		return parseProxyResponse(response);
	}

	private List<ProxyAddress> parseProxyResponse(String response) {
		if (response == null || response.trim().isEmpty()) {
			return List.of();
		}

		return Arrays.stream(response.split("\n"))
			.map(String::trim)
			.filter(line -> !line.isEmpty() && !line.startsWith("//"))
			.map(this::parseProxyLine)
			.toList();
	}

	private ProxyAddress parseProxyLine(String line) {
		String[] parts = line.split(":");
		if (parts.length != 4) {
			throw new IllegalArgumentException("Invalid proxy format: " + line);
		}

		try {
			String host = parts[0].trim();
			int port = Integer.parseInt(parts[1].trim());
			String username = parts[2].trim();
			String password = parts[3].trim();

			return new ProxyAddress(host, port, username, password);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid port number in proxy line: " + line, e);
		}
	}

}
