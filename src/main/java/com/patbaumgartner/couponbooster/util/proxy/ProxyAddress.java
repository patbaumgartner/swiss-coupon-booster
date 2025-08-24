package com.patbaumgartner.couponbooster.util.proxy;

import org.jetbrains.annotations.NotNull;

public record ProxyAddress(String host, int port, String username, String password) {

	@NotNull
	@Override
	public String toString() {
		return username + ":" + password + "@" + host + ":" + port;
	}
}
