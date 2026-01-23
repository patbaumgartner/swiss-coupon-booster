package com.patbaumgartner.couponbooster.util.cookie;

import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NetscapeCookieParserTest {

	@TempDir
	Path tempDir;

	@Test
	void parseLine_withValidCookie_shouldParseCorrectly() {
		// Given - use a future timestamp
		String line = ".example.com\tTRUE\t/\tTRUE\t9999999999\tsession_id\tabc123";

		// When
		Cookie cookie = NetscapeCookieParser.parseLine(line);

		// Then
		assertThat(cookie.name).isEqualTo("session_id");
		assertThat(cookie.value).isEqualTo("abc123");
		assertThat(cookie.domain).isEqualTo(".example.com");
		assertThat(cookie.path).isEqualTo("/");
		assertThat(cookie.secure).isTrue();
		assertThat(cookie.expires).isEqualTo(9999999999.0);
	}

	@Test
	void parseLine_withNonSecureCookie_shouldParseCorrectly() {
		// Given
		String line = "example.com\tFALSE\t/path\tFALSE\t0\ttest_cookie\ttest_value";

		// When
		Cookie cookie = NetscapeCookieParser.parseLine(line);

		// Then
		assertThat(cookie.name).isEqualTo("test_cookie");
		assertThat(cookie.value).isEqualTo("test_value");
		assertThat(cookie.domain).isEqualTo("example.com");
		assertThat(cookie.path).isEqualTo("/path");
		assertThat(cookie.secure).isFalse();
		assertThat(cookie.expires).isEqualTo(-1.0); // Session cookie
	}

	@Test
	void parseLine_withEmptyLine_shouldThrowException() {
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine("")).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("empty");
	}

	@Test
	void parseLine_withNullLine_shouldThrowException() {
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine(null)).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("empty");
	}

	@Test
	void parseLine_withInvalidFieldCount_shouldThrowException() {
		// Given - only 5 fields instead of 7
		String line = ".example.com\tTRUE\t/\tTRUE\t1234567890";

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine(line)).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("Expected 7 fields");
	}

	@Test
	void parseLine_withInvalidExpiration_shouldThrowException() {
		// Given
		String line = ".example.com\tTRUE\t/\tTRUE\tinvalid_timestamp\tsession_id\tabc123";

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine(line)).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("expiration");
	}

	@Test
	void parseLine_withEmptyCookieName_shouldThrowException() {
		// Given - use future timestamp so the expiration check doesn't fail first
		String line = ".example.com\tTRUE\t/\tTRUE\t9999999999\t\tvalue";

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine(line)).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("name cannot be empty");
	}

	@Test
	void parseFromFile_withValidFile_shouldParseAllCookies() throws Exception {
		// Given
		Path cookieFile = tempDir.resolve("cookies.txt");
		String content = """
				# Netscape HTTP Cookie File
				# This is a comment
				.example.com\tTRUE\t/\tTRUE\t9999999999\tsession_id\tabc123
				.example.com\tTRUE\t/api\tFALSE\t9999999999\tapi_token\ttoken123
				""";
		Files.writeString(cookieFile, content);

		// When
		List<Cookie> cookies = NetscapeCookieParser.parseFromFile(cookieFile);

		// Then
		assertThat(cookies).hasSize(2);
		assertThat(cookies.get(0).name).isEqualTo("session_id");
		assertThat(cookies.get(1).name).isEqualTo("api_token");
	}

	@Test
	void parseFromFile_withEmptyFile_shouldReturnEmptyList() throws Exception {
		// Given
		Path cookieFile = tempDir.resolve("empty_cookies.txt");
		Files.writeString(cookieFile, "# Only comments\n\n");

		// When
		List<Cookie> cookies = NetscapeCookieParser.parseFromFile(cookieFile);

		// Then
		assertThat(cookies).isEmpty();
	}

	@Test
	void parseFromFile_withMixedValidAndInvalidLines_shouldParseValidOnes() throws Exception {
		// Given
		Path cookieFile = tempDir.resolve("mixed_cookies.txt");
		String content = """
				# Netscape HTTP Cookie File
				.example.com\tTRUE\t/\tTRUE\t9999999999\tsession_id\tabc123
				invalid_line_with_too_few_fields
				.example.com\tTRUE\t/api\tFALSE\t9999999999\tapi_token\ttoken123
				""";
		Files.writeString(cookieFile, content);

		// When
		List<Cookie> cookies = NetscapeCookieParser.parseFromFile(cookieFile);

		// Then
		assertThat(cookies).hasSize(2); // Only valid cookies parsed
		assertThat(cookies.get(0).name).isEqualTo("session_id");
		assertThat(cookies.get(1).name).isEqualTo("api_token");
	}

	@Test
	void parseFromFile_withNullPath_shouldThrowException() {
		assertThatThrownBy(() -> NetscapeCookieParser.parseFromFile(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("null");
	}

	@Test
	void parseFromFile_withNonExistentFile_shouldThrowException() {
		// Given
		Path nonExistentFile = tempDir.resolve("non_existent.txt");

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseFromFile(nonExistentFile)).isInstanceOf(IOException.class)
			.hasMessageContaining("not found");
	}

	@Test
	void parseFromFile_withDirectory_shouldThrowException() {
		// Given
		Path directory = tempDir.resolve("some_directory");
		try {
			Files.createDirectory(directory);
		}
		catch (IOException e) {
			fail("Failed to create test directory", e);
		}

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseFromFile(directory)).isInstanceOf(IOException.class)
			.hasMessageContaining("not a regular file");
	}

	@Test
	void parseLine_withDataDomeCookie_shouldParseCorrectly() {
		// Given - Real-world example similar to the one in .env (with future timestamp)
		String line = ".supercard.ch\tTRUE\t/\tTRUE\t9999999999\tdatadome\trExg2hWbLopl8nuwV1eTIb2JWhyxkI0rWlS9JZCQP9G4j3dld_0W89U5E7N9W5dhqjtCKohkslErilbKjE_z_XEn~DxbiSS5VHUHjNm8LMsYjGqjzgnT64sFlko2AjNC";

		// When
		Cookie cookie = NetscapeCookieParser.parseLine(line);

		// Then
		assertThat(cookie.name).isEqualTo("datadome");
		assertThat(cookie.value).isEqualTo(
				"rExg2hWbLopl8nuwV1eTIb2JWhyxkI0rWlS9JZCQP9G4j3dld_0W89U5E7N9W5dhqjtCKohkslErilbKjE_z_XEn~DxbiSS5VHUHjNm8LMsYjGqjzgnT64sFlko2AjNC");
		assertThat(cookie.domain).isEqualTo(".supercard.ch");
		assertThat(cookie.path).isEqualTo("/");
		assertThat(cookie.secure).isTrue();
	}

	@Test
	void parseLine_withEmptyPath_shouldDefaultToRoot() {
		// Given
		String line = ".example.com\tTRUE\t\tTRUE\t9999999999\ttest\tvalue";

		// When
		Cookie cookie = NetscapeCookieParser.parseLine(line);

		// Then
		assertThat(cookie.path).isEqualTo("/");
	}

	@Test
	void parseLine_withExpiredCookie_shouldThrowException() {
		// Given - timestamp from 2009 (clearly expired)
		String line = ".example.com\tTRUE\t/\tTRUE\t1234567890\texpired_cookie\tvalue";

		// Then
		assertThatThrownBy(() -> NetscapeCookieParser.parseLine(line)).isInstanceOf(CookieParseException.class)
			.hasMessageContaining("expired");
	}

	@Test
	void parseFromFile_withExpiredCookie_shouldSkipExpiredCookies() throws Exception {
		// Given
		Path cookieFile = tempDir.resolve("expired_cookies.txt");
		String content = """
				# Netscape HTTP Cookie File
				.example.com\tTRUE\t/\tTRUE\t1234567890\texpired_cookie\told_value
				.example.com\tTRUE\t/api\tFALSE\t9999999999\tvalid_cookie\tvalid_value
				""";
		Files.writeString(cookieFile, content);

		// When
		List<Cookie> cookies = NetscapeCookieParser.parseFromFile(cookieFile);

		// Then - expired cookie should be skipped, only valid cookie parsed
		assertThat(cookies).hasSize(1);
		assertThat(cookies.get(0).name).isEqualTo("valid_cookie");
	}

}
