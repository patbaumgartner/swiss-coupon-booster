package com.patbaumgartner.couponbooster.util.proxy;

import org.jetbrains.annotations.NotNull;

public record ProxyAddress(String host, int port, String username, String password) {

	@NotNull
	@Override
	public String toString() {
		String safeUsername = username != null ? username : "";
		String safePassword = password != null ? password : "";
		String safeHost = host != null ? host : "";

		return safeUsername + ":" + safePassword + "@" + safeHost + ":" + port;
	}
}
