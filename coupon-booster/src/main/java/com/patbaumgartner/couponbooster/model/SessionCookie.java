package com.patbaumgartner.couponbooster.model;

import java.util.Objects;

/**
 * An immutable session cookie captured by the Patchright login sidecar.
 * <p>
 * Only the three attributes the application actually consumes are modelled: {@code name}
 * and {@code value} build the {@code Cookie} request header, and {@code domain} decides
 * which retailer's API a cookie may be sent to. Attributes such as {@code path},
 * {@code secure}, {@code httpOnly} and {@code expires} are deliberately not mirrored —
 * nothing reads them, and a value object that carries unused state invites the assumption
 * that it is enforced.
 *
 * @param name the cookie name, never {@code null}
 * @param value the cookie value, never {@code null}
 * @param domain the cookie domain, normalised to {@code ""} when absent
 */
public record SessionCookie(String name, String value, String domain) {

	public SessionCookie {
		Objects.requireNonNull(name, "Cookie name cannot be null");
		Objects.requireNonNull(value, "Cookie value cannot be null");
		domain = (domain == null) ? "" : domain;
	}

	/**
	 * Tests whether this cookie may be sent to the given host, using RFC 6265 domain
	 * matching.
	 * <p>
	 * A domain cookie ({@code .example.com}) matches {@code example.com} and every
	 * subdomain of it. A host-only cookie ({@code www.example.com}) matches that host
	 * alone. A cookie without a domain matches nothing, because there is no evidence it
	 * belongs to the target host.
	 * <p>
	 * This is what keeps one retailer's session cookies from being attached to the other
	 * retailer's API requests.
	 * @param host the target host, e.g. {@code www.supercard.ch}
	 * @return {@code true} if the cookie belongs to {@code host}
	 */
	public boolean matchesHost(String host) {
		if (host == null || host.isBlank() || domain.isBlank()) {
			return false;
		}
		String cookieDomain = domain.startsWith(".") ? domain.substring(1) : domain;
		if (cookieDomain.isBlank()) {
			return false;
		}
		String normalisedHost = toAsciiLowerCase(host);
		String normalisedDomain = toAsciiLowerCase(cookieDomain);
		return normalisedHost.equals(normalisedDomain) || normalisedHost.endsWith("." + normalisedDomain);
	}

	/**
	 * Folds ASCII letters only. Locale-sensitive {@code String.toLowerCase} is unsafe
	 * here: it maps {@code U+212A KELVIN SIGN} onto ASCII {@code k}, which would let a
	 * look-alike domain match a real one.
	 * @param value the host or cookie domain to normalise
	 * @return {@code value} with {@code A}-{@code Z} replaced by {@code a}-{@code z}
	 */
	private static String toAsciiLowerCase(String value) {
		StringBuilder normalised = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			normalised.append((character >= 'A' && character <= 'Z') ? (char) (character + ('a' - 'A')) : character);
		}
		return normalised.toString();
	}

}
