package com.patbaumgartner.couponbooster.coop.config;

public record ProxyAddress(String host, int port) {

	public String asUserPassUri(String user, String pass) {
		return user + ":" + pass + "@" + host + ":" + port;
	}
}