package com.patbaumgartner.couponbooster.util.proxy;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * A component for resolving and managing a list of proxy addresses.
 * <p>
 * This resolver fetches a list of proxies from a configured URL, parses them, and
 * provides methods for retrieving them either sequentially or randomly. It also handles
 * automatic refetching of the proxy list when it is exhausted.
 *
 * @see ProxyProperties
 * @see ProxyAddress
 */
@Component
public class ProxyResolver {

	private final RestClient restClient;

	private final ProxyProperties proxyProperties;

	private List<ProxyAddress> currentProxyList = List.of();

	private final SecureRandom random = new SecureRandom();

	private int currentIndex;

	/**
	 * Constructs a new {@code ProxyResolver} with the specified dependencies.
	 * @param restClientBuilder the builder for creating the {@link RestClient} instance
	 * @param proxyProperties the configuration properties for the proxy resolver
	 */
	public ProxyResolver(RestClient.Builder restClientBuilder, ProxyProperties proxyProperties) {
		this.restClient = restClientBuilder.build();
		this.proxyProperties = proxyProperties;
	}

	/**
	 * Returns the next proxy in the list in a sequential round-robin order.
	 * @return the next ProxyAddress
	 */
	public synchronized ProxyAddress getNextProxy() {
		ensureProxyListAvailable();
		if (currentProxyList.isEmpty()) {
			throw new IllegalStateException("No proxies available from the API");
		}
		// Keep index within bounds even if list size changed
		if (currentIndex >= currentProxyList.size()) {
			currentIndex = 0;
		}

		currentIndex = (currentIndex + 1) % currentProxyList.size();
		return currentProxyList.get(currentIndex);
	}

	/**
	 * Returns the proxy at the current index without advancing.
	 * @return the current ProxyAddress
	 */
	public synchronized ProxyAddress getCurrentProxy() {
		ensureProxyListAvailable();
		if (currentProxyList.isEmpty()) {
			throw new IllegalStateException("No proxies available from the API");
		}

		// Make sure currentIndex is in range (wrap if necessary)
		if (currentIndex >= currentProxyList.size()) {
			currentIndex = 0;
		}

		return currentProxyList.get(currentIndex);
	}

	/**
	 * Returns a random proxy from the list.
	 * @return a random ProxyAddress
	 */
	public synchronized ProxyAddress getRandomProxy() {
		ensureProxyListAvailable();
		if (currentProxyList.isEmpty()) {
			throw new IllegalStateException("No proxies available from the API");
		}

		int randomIndex = random.nextInt(currentProxyList.size());
		return currentProxyList.get(randomIndex);
	}

	/**
	 * Resets the internal iterator and forces a refetch of the proxy list on the next
	 * call.
	 */
	public synchronized void resetIterator() {
		currentIndex = 0;
		currentProxyList = List.of(); // Force refetch on next call
	}

	/**
	 * Returns the number of remaining proxies in the list that have not yet been consumed
	 * by {@link #getNextProxy()}.
	 * @return the number of remaining proxies
	 */
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

	/**
	 * Fetches the list of proxies from the configured URL.
	 * @return a list of {@link ProxyAddress} objects
	 */
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
