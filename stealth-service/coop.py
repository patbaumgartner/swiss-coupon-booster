"""Coop Supercard stealth login flow.

Navigates to supercard.ch, handles DataDome fingerprinting, accepts the cookie
banner, performs password-based login, and returns session cookies.

The persistent browser profile is stored in COOP_USER_DATA_DIR so that the
DataDome cookie persists across runs — greatly reducing the chance of a
challenge on subsequent executions.
"""

from __future__ import annotations

import logging
from typing import Any

from patchright.async_api import BrowserContext, Page, async_playwright

from browser import (
    create_persistent_context,
    dump_debug_artifacts,
    is_datadome_challenge,
    random_delay,
    serialize_cookies,
    wait_for_datadome_resolution,
)
from config import (
    COOP_LOGIN_URL,
    COOP_USER_DATA_DIR,
    COOKIE_CONSENT_WAIT_MAX,
    COOKIE_CONSENT_WAIT_MIN,
    DATADOME_WAIT_MAX,
    DATADOME_WAIT_MIN,
    INPUT_WAIT_MAX,
    INPUT_WAIT_MIN,
    LOGIN_CLICK_WAIT_MAX,
    LOGIN_CLICK_WAIT_MIN,
    SEL_COOP_COOKIE_ACCEPT,
    SEL_COOP_LOGIN_LINK,
    SEL_COOP_PASSWORD,
    SEL_COOP_SUBMIT,
    SEL_COOP_USERNAME,
    SUBMIT_WAIT_MAX,
    SUBMIT_WAIT_MIN,
    TIMEOUT_MS,
    TYPING_DELAY_MS,
)

log = logging.getLogger("stealth-service.coop")


# ── Private helpers ───────────────────────────────────────────────────────────


async def _handle_cookie_consent(page: Page) -> None:
    await random_delay(COOKIE_CONSENT_WAIT_MIN, COOKIE_CONSENT_WAIT_MAX)
    try:
        btn = page.locator(SEL_COOP_COOKIE_ACCEPT).first
        await btn.wait_for(timeout=3000)
        if await btn.is_visible():
            await btn.click()
            await page.wait_for_load_state("load")
            log.debug("Accepted cookie consent")
    except Exception:  # noqa: BLE001
        log.debug("Cookie consent not shown or already dismissed")


async def _is_login_needed(page: Page) -> bool:
    try:
        btn = page.locator(SEL_COOP_LOGIN_LINK).first
        await btn.wait_for(timeout=5000)
        return await btn.is_visible()
    except Exception:  # noqa: BLE001
        return False


async def _fill_login_form(page: Page, email: str, password: str) -> None:
    log.debug("Clicking login link")
    await random_delay(LOGIN_CLICK_WAIT_MIN, LOGIN_CLICK_WAIT_MAX)
    login_link = page.locator(SEL_COOP_LOGIN_LINK).first
    await login_link.wait_for(timeout=TIMEOUT_MS)
    await login_link.click()

    log.debug("Entering e-mail")
    await random_delay(INPUT_WAIT_MIN, INPUT_WAIT_MAX)
    username_field = page.locator(SEL_COOP_USERNAME).first
    await username_field.wait_for(timeout=TIMEOUT_MS)
    await username_field.clear()
    await username_field.press_sequentially(email, delay=TYPING_DELAY_MS)

    log.debug("Entering password")
    await random_delay(INPUT_WAIT_MIN, INPUT_WAIT_MAX)
    password_field = page.locator(SEL_COOP_PASSWORD).first
    await password_field.wait_for(timeout=TIMEOUT_MS)
    await password_field.clear()
    await password_field.press_sequentially(password, delay=TYPING_DELAY_MS)

    log.debug("Submitting login form")
    await random_delay(SUBMIT_WAIT_MIN, SUBMIT_WAIT_MAX)
    submit_btn = page.locator(SEL_COOP_SUBMIT).first
    await submit_btn.wait_for(timeout=TIMEOUT_MS)
    await submit_btn.click()

    # Use "load" — the Supercard SPA keeps background polling requests running
    # indefinitely, so "networkidle" never fires.
    await page.wait_for_load_state("load", timeout=TIMEOUT_MS)
    log.info("Login form submitted")


async def _run_login_flow(context: BrowserContext, email: str, password: str) -> dict[str, Any]:
    page = await context.new_page()
    try:
        user_agent: str = await page.evaluate("() => navigator.userAgent")
        language: str = await page.evaluate("() => navigator.language")
        log.debug("User-agent: %s", user_agent)

        # Preserve the DataDome cookie across sessions; clear everything else.
        existing = await context.cookies()
        dd_cookie = next((c for c in existing if c["name"] == "datadome"), None)
        if existing:
            await context.clear_cookies()
            log.debug("Cleared %d existing cookies", len(existing))
            if dd_cookie:
                await context.add_cookies([dd_cookie])
                log.debug("Restored DataDome cookie")

        log.info("Navigating to Coop login URL: %s", COOP_LOGIN_URL)
        await page.goto(COOP_LOGIN_URL)
        await page.wait_for_load_state("load")

        # Critical: allow DataDome fingerprinting to complete before interacting.
        await random_delay(DATADOME_WAIT_MIN, DATADOME_WAIT_MAX)

        if is_datadome_challenge(page):
            resolved = await wait_for_datadome_resolution(page, timeout_ms=30000)
            if not resolved:
                await dump_debug_artifacts(page, "coop_datadome_challenge")
                raise RuntimeError(
                    "DataDome challenge detected and did not auto-resolve. "
                    "Run on a residential IP or seed the persistent profile with cookies. "
                    "Debug artifacts saved."
                )

        await _handle_cookie_consent(page)

        if await _is_login_needed(page):
            await _fill_login_form(page, email, password)
        else:
            log.info("Already logged in via persistent session")

        if is_datadome_challenge(page):
            resolved = await wait_for_datadome_resolution(page, timeout_ms=15000)
            if not resolved:
                await dump_debug_artifacts(page, "coop_datadome_challenge_post_login")
                raise RuntimeError("DataDome challenge appeared after login and did not resolve.")

        cookies_raw = await context.cookies()
        log.info("Collected %d session cookies", len(cookies_raw))
        return {"cookies": serialize_cookies(cookies_raw), "userAgent": user_agent, "language": language}

    except Exception:
        await dump_debug_artifacts(page, "coop_login_error")
        raise
    finally:
        await page.close()


# ── Public API ────────────────────────────────────────────────────────────────


async def coop_stealth_login(email: str, password: str) -> dict[str, Any]:
    """Perform a stealth Coop Supercard login using Patchright.

    Returns:
        A dict with keys ``cookies`` (list), ``userAgent`` (str), ``language`` (str).

    Raises:
        RuntimeError: on DataDome challenge or login failure.
    """
    COOP_USER_DATA_DIR.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as pw:
        context = await create_persistent_context(pw, COOP_USER_DATA_DIR)
        try:
            return await _run_login_flow(context, email, password)
        finally:
            await context.close()
