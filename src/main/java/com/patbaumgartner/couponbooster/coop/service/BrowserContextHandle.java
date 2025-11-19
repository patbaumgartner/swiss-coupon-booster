package com.patbaumgartner.couponbooster.coop.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;

/**
 * A wrapper around {@link BrowserContext} that also manages the lifecycle of the
 * underlying {@link Browser} if applicable.
 * <p>
 * This is necessary because when using a persistent context, the context itself controls
 * the browser process. When using an ephemeral context (browser.newContext()), closing
 * the context does not close the browser. This handle ensures that the browser is
 * properly closed in both cases.
 */
public record BrowserContextHandle(BrowserContext context, Browser browser) implements AutoCloseable {

	/**
	 * Creates a handle for a persistent context (where no separate Browser instance
	 * exists).
	 * @param context the persistent browser context
	 * @return a new handle
	 */
	public static BrowserContextHandle persistent(BrowserContext context) {
		return new BrowserContextHandle(context, null);
	}

	/**
	 * Creates a handle for an ephemeral context (created from a Browser instance).
	 * @param context the browser context
	 * @param browser the underlying browser instance
	 * @return a new handle
	 */
	public static BrowserContextHandle ephemeral(BrowserContext context, Browser browser) {
		return new BrowserContextHandle(context, browser);
	}

	public BrowserContext get() {
		return context;
	}

	@Override
	public void close() {
		context.close();
		if (browser != null) {
			browser.close();
		}
	}

}
