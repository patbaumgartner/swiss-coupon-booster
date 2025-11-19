package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for authentication services using Playwright.
 * <p>
 * Provides common utility methods for interacting with web elements, such as typing,
 * clicking, and finding elements with explicit waits.
 */
public abstract class AbstractAuthenticationService implements AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(AbstractAuthenticationService.class);

	/**
	 * Types text into a field identified by the selector.
	 * @param page the Playwright page
	 * @param selector the CSS selector of the field
	 * @param text the text to type
	 * @param delayMs the delay between keystrokes in milliseconds (0 for no delay)
	 */
	protected void typeIntoField(Page page, String selector, String text, int delayMs) {
		var field = findElement(page, selector);
		field.clear();
		if (delayMs > 0) {
			field.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(delayMs));
		}
		else {
			field.fill(text);
		}
	}

	/**
	 * Clicks an element identified by the selector.
	 * @param page the Playwright page
	 * @param selector the CSS selector of the element
	 */
	protected void clickElement(Page page, String selector) {
		findElement(page, selector).click();
	}

	/**
	 * Finds an element by selector, waiting for it to be visible.
	 * @param page the Playwright page
	 * @param selector the CSS selector
	 * @return the found Locator
	 * @throws CouponBoosterException if the element is not found or not visible
	 */
	protected Locator findElement(Page page, String selector) {
		try {
			var element = page.locator(selector);
			element.first().waitFor(new Locator.WaitForOptions().setTimeout(20000));

			if (!element.first().isVisible()) {
				throw new CouponBoosterException("Element not visible: " + selector);
			}

			return element;
		}
		catch (TimeoutError e) {
			throw new CouponBoosterException("Element not found: " + selector, e);
		}
	}

	private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

	/**
	 * Checks if a string is null or empty/blank.
	 * @param value the string to check
	 * @return true if null or blank, false otherwise
	 */
	protected static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Adds a random delay to simulate human behavior and avoid detection.
	 * @param minMs minimum delay in milliseconds
	 * @param maxMs maximum delay in milliseconds
	 */
	protected void addRandomDelay(int minMs, int maxMs) {
		try {
			int delay = minMs + SECURE_RANDOM.nextInt(maxMs - minMs + 1);
			if (log.isTraceEnabled()) {
				log.trace("Adding random delay of {}ms", delay);
			}
			Thread.sleep(delay);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Random delay interrupted", e);
		}
	}

}
