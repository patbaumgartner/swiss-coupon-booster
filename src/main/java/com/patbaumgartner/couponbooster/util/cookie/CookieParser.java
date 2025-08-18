package com.patbaumgartner.couponbooster.util.cookie;

import java.net.IDN;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A utility for parsing a single {@code Set-Cookie} header value into an immutable
 * {@link ParsedCookie} object.
 * <p>
 * This parser handles the various attributes of a cookie, such as name, value, domain,
 * path, max-age, expires, secure, httpOnly, and SameSite. It is designed to be robust and
 * handle common variations in the {@code Set-Cookie} header format.
 *
 * @see ParsedCookie
 * @see CookieParseException
 */
public final class CookieParser {

	private CookieParser() {
	}

	// RFC 1123 date format for Expires
	private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

	/**
	 * Parses a single {@code Set-Cookie} header value.
	 * @param setCookieHeader the {@code Set-Cookie} header value to parse
	 * @return a {@link ParsedCookie} object representing the parsed cookie
	 * @throws CookieParseException if the header is null, empty, or malformed
	 */
	public static ParsedCookie parse(String setCookieHeader) {
		if (setCookieHeader == null) {
			throw new CookieParseException("Header is null");
		}
		String header = Normalizer.normalize(setCookieHeader, Normalizer.Form.NFC).trim();
		if (header.isEmpty()) {
			throw new CookieParseException("Header is empty");
		}

		String[] tokens = header.split(";", -1);
		if (tokens.length == 0) {
			throw new CookieParseException("No tokens found");
		}

		String nv = tokens[0].trim();
		int eq = nv.indexOf('=');
		if (eq <= 0) {
			throw new CookieParseException("Missing or empty cookie name/value pair");
		}

		String name = nv.substring(0, eq).trim().toLowerCase(Locale.ROOT);
		String value = nv.substring(eq + 1).trim();

		String domain = null;
		String path = null;
		Long maxAge = null;
		OffsetDateTime expires = null;
		boolean secure = false;
		boolean httpOnly = false;
		SameSite sameSite = SameSite.Unknown;

		Map<String, String> extensions = new LinkedHashMap<>();

		for (int i = 1; i < tokens.length; i++) {
			String raw = tokens[i].trim();
			if (raw.isEmpty()) {
				continue;
			}

			int idx = raw.indexOf('=');
			String key = (idx >= 0 ? raw.substring(0, idx) : raw).trim().toLowerCase(Locale.ROOT);
			String val = (idx >= 0 ? raw.substring(idx + 1) : "").trim();

			switch (key) {
				case "domain" -> {
					if (!val.isEmpty()) {
						if (val.startsWith(".")) {
							// Preserve leading dot for wildcard domains
							String domainWithoutDot = val.substring(1);
							if (!domainWithoutDot.isEmpty()) {
								domain = "." + IDN.toASCII(domainWithoutDot);
							}
							else {
								domain = val; // Just a dot, keep as-is
							}
						}
						else {
							domain = IDN.toASCII(val);
						}
					}
				}
				case "path" -> path = val.isEmpty() ? "/" : val;
				case "max-age" -> {
					try {
						maxAge = Long.parseLong(val);
					}
					catch (NumberFormatException e) {
						throw new CookieParseException("Invalid Max-Age: " + val, e);
					}
				}
				case "expires" -> {
					try {
						expires = OffsetDateTime.parse(val, RFC_1123);
					}
					catch (DateTimeParseException e) {
						throw new CookieParseException("Invalid Expires date: " + val, e);
					}
				}
				case "secure" -> secure = true;
				case "httponly" -> httpOnly = true;
				case "samesite" -> sameSite = SameSite.from(val);
				default -> extensions.put(key, val);
			}
		}

		return new ParsedCookie(name, value, domain, path, maxAge, expires, secure, httpOnly, sameSite, extensions);
	}

}
