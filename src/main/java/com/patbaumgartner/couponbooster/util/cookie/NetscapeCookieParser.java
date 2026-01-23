package com.patbaumgartner.couponbooster.util.cookie;

import com.microsoft.playwright.options.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility for parsing cookies from a Netscape format cookies.txt file.
 * <p>
 * The Netscape cookie file format is a tab-separated format commonly used by curl and
 * other HTTP tools. Each line represents a cookie with the following format:
 *
 * <pre>
 * domain    flag    path    secure    expiration    name    value
 * </pre>
 *
 * Lines starting with '#' are treated as comments and ignored.
 * <p>
 * Example:
 *
 * <pre>
 * # Netscape HTTP Cookie File
 * .example.com    TRUE    /    TRUE    1234567890    session_id    abc123
 * </pre>
 *
 * @see Cookie
 */
public final class NetscapeCookieParser {

	private static final Logger log = LoggerFactory.getLogger(NetscapeCookieParser.class);

	private static final int NETSCAPE_COOKIE_FIELDS = 7;

	private NetscapeCookieParser() {
		// Utility class
	}

	/**
	 * Parses cookies from a Netscape format cookies.txt file.
	 * @param cookieFilePath the path to the cookies.txt file
	 * @return a list of parsed cookies
	 * @throws IOException if an error occurs reading the file
	 * @throws CookieParseException if the file format is invalid
	 */
	public static List<Cookie> parseFromFile(Path cookieFilePath) throws IOException {
		if (cookieFilePath == null) {
			throw new IllegalArgumentException("Cookie file path cannot be null");
		}

		if (!Files.exists(cookieFilePath)) {
			throw new IOException("Cookie file not found: " + cookieFilePath);
		}

		if (!Files.isRegularFile(cookieFilePath)) {
			throw new IOException("Cookie file path is not a regular file: " + cookieFilePath);
		}

		List<Cookie> cookies = new ArrayList<>();

		try (BufferedReader reader = Files.newBufferedReader(cookieFilePath)) {
			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = line.trim();

				// Skip empty lines and comments
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				try {
					Cookie cookie = parseLine(line);
					cookies.add(cookie);
					if (log.isDebugEnabled()) {
						log.debug("Parsed cookie: {} from domain: {}", cookie.name, cookie.domain);
					}
				}
				catch (CookieParseException e) {
					log.error("Failed to parse cookie at line {}: {}", lineNumber, e.getMessage(), e);
					// Continue parsing other cookies
				}
			}
		}

		if (log.isInfoEnabled()) {
			log.info("Loaded {} cookies from {}", cookies.size(), cookieFilePath);
		}

		return cookies;
	}

	/**
	 * Parses a single line in Netscape cookie format.
	 * <p>
	 * Expected format (tab-separated):
	 *
	 * <pre>
	 * domain    flag    path    secure    expiration    name    value
	 * </pre>
	 * @param line the line to parse
	 * @return the parsed cookie
	 * @throws CookieParseException if the line format is invalid
	 */
	static Cookie parseLine(String line) {
		if (line == null || line.isEmpty()) {
			throw new CookieParseException("Cookie line is empty");
		}

		// Split by tab or any whitespace
		String[] fields = line.split("\\t");

		if (fields.length != NETSCAPE_COOKIE_FIELDS) {
			throw new CookieParseException("Invalid Netscape cookie format. Expected 7 fields, got " + fields.length);
		}

		try {
			String domain = fields[0].trim();
			// fields[1] is the "flag" (TRUE/FALSE for subdomain matching) - not used in
			// Playwright Cookie
			String path = fields[2].trim();
			// Parse secure flag - Netscape format uses "TRUE" or "FALSE"
			// (case-insensitive in practice)
			String secureField = fields[3].trim();
			boolean secure = "TRUE".equals(secureField) || "true".equals(secureField);
			// fields[4] is expiration timestamp - Playwright Cookie uses expires as
			// Double
			// (Unix timestamp)
			double expires = parseExpiration(fields[4].trim());
			String name = fields[5].trim();
			String value = fields[6].trim();

			if (name.isEmpty()) {
				throw new CookieParseException("Cookie name cannot be empty");
			}

			// Build the Playwright Cookie
			Cookie cookie = new Cookie(name, value);
			cookie.setDomain(domain);
			cookie.setPath(path.isEmpty() ? "/" : path);
			cookie.setSecure(secure);

			// Set expiration (Playwright uses Unix timestamp in seconds as Double)
			// For session cookies (expires <= 0), set to -1
			cookie.setExpires(expires);

			// Note: Netscape format doesn't include HttpOnly or SameSite attributes
			// These will use Playwright defaults

			return cookie;
		}
		catch (NumberFormatException e) {
			throw new CookieParseException("Invalid expiration timestamp: " + fields[4], e);
		}
	}

	/**
	 * Parses the expiration timestamp from the Netscape cookie format.
	 * @param expirationStr the expiration string (Unix timestamp in seconds)
	 * @return the expiration as a double (Unix timestamp), or -1 if invalid/expired
	 * @throws CookieParseException if the cookie is expired
	 */
	private static double parseExpiration(String expirationStr) {
		try {
			long expirationSeconds = Long.parseLong(expirationStr);

			// Netscape format uses 0 to indicate session cookie
			if (expirationSeconds == 0) {
				return -1; // Session cookie
			}

			// Check if the cookie is already expired
			long currentTimeSeconds = System.currentTimeMillis() / 1000;
			if (expirationSeconds < currentTimeSeconds) {
				throw new CookieParseException("Cookie has expired (expiration: " + expirationSeconds + ", current: "
						+ currentTimeSeconds + ")");
			}

			return (double) expirationSeconds;
		}
		catch (NumberFormatException e) {
			throw new CookieParseException("Invalid expiration format: " + expirationStr, e);
		}
	}

}
