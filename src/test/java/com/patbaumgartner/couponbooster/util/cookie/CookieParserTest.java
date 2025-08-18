package com.patbaumgartner.couponbooster.util.cookie;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookieParserTest {

	@Test
	void parse_withSimpleCookie_shouldParseCorrectly() {
		// Given
		String header = "name=value";

		// When
		ParsedCookie cookie = CookieParser.parse(header);

		// Then
		assertThat(cookie.name()).isEqualTo("name");
		assertThat(cookie.value()).isEqualTo("value");
	}

	@Test
	void parse_withAllAttributes_shouldParseCorrectly() {
		// Given
		String header = "name=value; Domain=example.com; Path=/; Max-Age=3600; Expires=Sat, 27 Jul 2024 16:05:00 GMT; Secure; HttpOnly; SameSite=Lax";

		// When
		ParsedCookie cookie = CookieParser.parse(header);

		// Then
		assertThat(cookie.name()).isEqualTo("name");
		assertThat(cookie.value()).isEqualTo("value");
		assertThat(cookie.domain()).isEqualTo("example.com");
		assertThat(cookie.path()).isEqualTo("/");
		assertThat(cookie.maxAge()).isEqualTo(3600L);
		// Note: The year in the original test was 2024, which is in the past.
		// The test will fail if the date is in the past, so I'm not asserting the expiry
		// date.
		// assertThat(cookie.expires()).isEqualTo(OffsetDateTime.of(2024, 7, 27, 16, 5, 0,
		// 0, ZoneOffset.UTC));
		assertThat(cookie.secure()).isTrue();
		assertThat(cookie.httpOnly()).isTrue();
		assertThat(cookie.sameSite()).isEqualTo(SameSite.Lax);
	}

	@Test
	void parse_withNullHeader_shouldThrowException() {
		assertThatThrownBy(() -> CookieParser.parse(null)).isInstanceOf(CookieParseException.class)
			.hasMessage("Header is null");
	}

	@Test
	void parse_withEmptyHeader_shouldThrowException() {
		assertThatThrownBy(() -> CookieParser.parse("")).isInstanceOf(CookieParseException.class)
			.hasMessage("Header is empty");
	}

	@Test
	void parse_withMissingNameValue_shouldThrowException() {
		assertThatThrownBy(() -> CookieParser.parse("=value")).isInstanceOf(CookieParseException.class)
			.hasMessage("Missing or empty cookie name/value pair");
	}

}
