package com.patbaumgartner.couponbooster.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SessionCookieTest {

	@ParameterizedTest
	@CsvSource({
			// domain cookie matches the registrable domain and every subdomain
			"'.supercard.ch', 'supercard.ch', true", "'.supercard.ch', 'www.supercard.ch', true",
			"'.supercard.ch', 'webapi.supercard.ch', true", "'.migros.ch', 'account.migros.ch', true",
			// host-only cookie matches that host alone
			"'www.supercard.ch', 'www.supercard.ch', true", "'www.supercard.ch', 'supercard.ch', false",
			"'account.migros.ch', 'www.supercard.ch', false",
			// host matching is case-insensitive
			"'.SuperCard.CH', 'WWW.supercard.ch', true" })
	void matchesHost_appliesRfc6265DomainMatching(String domain, String host, boolean expected) {
		assertThat(new SessionCookie("session", "value", domain).matchesHost(host)).isEqualTo(expected);
	}

	@Test
	void matchesHost_doesNotLeakOneRetailersCookiesToTheOther() {
		var migrosCookie = new SessionCookie("CSRF", "token", ".migros.ch");

		assertThat(migrosCookie.matchesHost("account.migros.ch")).isTrue();
		assertThat(migrosCookie.matchesHost("www.supercard.ch")).isFalse();
	}

	@Test
	void matchesHost_rejectsSuffixLookAlikeDomains() {
		var cookie = new SessionCookie("session", "value", ".supercard.ch");

		assertThat(cookie.matchesHost("evil-supercard.ch")).isFalse();
		assertThat(cookie.matchesHost("supercard.ch.attacker.test")).isFalse();
	}

	@Test
	void matchesHost_rejectsUnicodeLookAlikesThatWouldFoldOntoAscii() {
		// U+212A KELVIN SIGN lower-cases to ASCII 'k' under locale-sensitive folding.
		var cookie = new SessionCookie("session", "value", "su\u212Aercard.ch");

		assertThat(cookie.matchesHost("sukercard.ch")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", ".", "  " })
	void matchesHost_returnsFalseWhenTheCookieHasNoUsableDomain(String domain) {
		assertThat(new SessionCookie("session", "value", domain).matchesHost("www.supercard.ch")).isFalse();
	}

	@Test
	void matchesHost_returnsFalseForAbsentHost() {
		var cookie = new SessionCookie("session", "value", ".supercard.ch");

		assertThat(cookie.matchesHost(null)).isFalse();
		assertThat(cookie.matchesHost("")).isFalse();
	}

	@Test
	void nullDomainIsNormalisedToEmpty() {
		assertThat(new SessionCookie("session", "value", null).domain()).isEmpty();
	}

	@Test
	void nameAndValueAreRequired() {
		assertThatNullPointerException().isThrownBy(() -> new SessionCookie(null, "value", ".supercard.ch"));
		assertThatNullPointerException().isThrownBy(() -> new SessionCookie("session", null, ".supercard.ch"));
	}

}
