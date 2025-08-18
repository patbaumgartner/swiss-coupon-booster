package com.patbaumgartner.couponbooster.util.cookie;

import java.time.OffsetDateTime;
import java.util.Map;

public record ParsedCookie(String name, String value, String domain, String path, Long maxAge, OffsetDateTime expires,
		boolean secure, boolean httpOnly, SameSite sameSite, Map<String, String> extensions) {
	public ParsedCookie {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Cookie name cannot be null/blank");
		}
		if (value == null) {
			value = "";
		}
		if (sameSite == null) {
			sameSite = SameSite.Unknown;
		}
		extensions = Map.copyOf(extensions); // defensive copy
	}

	public String toCookieHeader() {
		return name + "=" + value;
	}
}
