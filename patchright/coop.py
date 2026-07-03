"""Coop Supercard stealth login flow.

Navigates to supercard.ch, handles DataDome fingerprinting, accepts the cookie
banner, performs password-based login, and returns session cookies.

The persistent browser profile is stored in COOP_USER_DATA_DIR so that the
DataDome cookie persists across runs — greatly reducing the chance of a
challenge on subsequent executions.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from patchright.async_api import BrowserContext, Page, async_playwright
from patchright.async_api import TimeoutError as PlaywrightTimeoutError

from browser import (
    create_persistent_context,
    dump_debug_artifacts,
    is_datadome_challenge,
    is_datadome_hard_block,
    random_delay,
    serialize_cookies,
    solve_datadome_slider,
    wait_for_datadome_resolution,
)
from config import (
    COOP_LOGIN_URL,
    COOP_NAV_MAX_ATTEMPTS,
    COOP_NAV_RETRY_BACKOFF_MS,
    COOP_NAV_TIMEOUT_MS,
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

log = logging.getLogger("patchright.coop")


# ── Private helpers ───────────────────────────────────────────────────────────


async def _navigate_to_login(page: Page) -> None:
    """Navigate to the Coop login URL with bounded retries.

    Uses ``wait_until="domcontentloaded"`` (not the default ``"load"``) so a slow
    SSO redirect or a DataDome interstitial that keeps sub-resources loading does
    not abort the navigation mid-flight. A dedicated, generous per-navigation
    timeout (``COOP_NAV_TIMEOUT_MS``) replaces the shared 30 s default, and the
    navigation is retried up to ``COOP_NAV_MAX_ATTEMPTS`` times with linear
    backoff.

    When a navigation attempt times out but the page has already landed on a
    DataDome interstitial, we stop retrying and return so the caller's DataDome
    handling can wait for the post-challenge navigation — retrying the ``goto``
    would only discard challenge progress.
    """
    last_exc: Exception | None = None
    for attempt in range(1, COOP_NAV_MAX_ATTEMPTS + 1):
        try:
            log.info(
                "Navigating to Coop login URL (attempt %d/%d, timeout %d ms): %s",
                attempt,
                COOP_NAV_MAX_ATTEMPTS,
                COOP_NAV_TIMEOUT_MS,
                COOP_LOGIN_URL,
            )
            await page.goto(
                COOP_LOGIN_URL,
                wait_until="domcontentloaded",
                timeout=COOP_NAV_TIMEOUT_MS,
            )
            log.info("Coop login DOM ready (landed on %s)", page.url)
            return
        except PlaywrightTimeoutError as exc:
            last_exc = exc
            # A DataDome interstitial keeps the page "loading" forever, which
            # surfaces here as a navigation timeout even though we did reach the
            # challenge. Hand off to the caller instead of retrying the goto.
            if is_datadome_challenge(page):
                log.info(
                    "Navigation timed out on a DataDome interstitial (url=%s); "
                    "proceeding to challenge handling",
                    page.url,
                )
                return
            log.warning(
                "Coop navigation attempt %d/%d timed out after %d ms (url=%s)",
                attempt,
                COOP_NAV_MAX_ATTEMPTS,
                COOP_NAV_TIMEOUT_MS,
                page.url,
            )
            if attempt < COOP_NAV_MAX_ATTEMPTS:
                backoff_ms = COOP_NAV_RETRY_BACKOFF_MS * attempt
                log.info("Backing off %d ms before retrying navigation", backoff_ms)
                await asyncio.sleep(backoff_ms / 1000.0)

    raise RuntimeError(
        f"Coop navigation to {COOP_LOGIN_URL} failed after {COOP_NAV_MAX_ATTEMPTS} "
        f"attempts (last error: {last_exc}). The SSO redirect or DataDome challenge "
        "did not settle in time."
    )

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

        await _navigate_to_login(page)

        # Critical: allow DataDome fingerprinting to complete before interacting.
        await random_delay(DATADOME_WAIT_MIN, DATADOME_WAIT_MAX)

        if is_datadome_challenge(page):
            resolved = await wait_for_datadome_resolution(page, timeout_ms=8000)
            if not resolved:
                # Browser-verification did not auto-clear — try the slider (t=fe).
                resolved = await solve_datadome_slider(page)
            if resolved:
                # The challenge cleared by redirecting back to the login page.
                # Wait for that post-challenge navigation to settle before we
                # start interacting, otherwise selectors resolve against the
                # interstitial's stale DOM.
                try:
                    await page.wait_for_load_state("domcontentloaded", timeout=COOP_NAV_TIMEOUT_MS)
                except PlaywrightTimeoutError:
                    log.warning("Post-challenge navigation did not settle within %d ms", COOP_NAV_TIMEOUT_MS)
            if not resolved:
                await dump_debug_artifacts(page, "coop_datadome_challenge")
                if await is_datadome_hard_block(page):
                    raise RuntimeError(
                        "DataDome served a terminal block page — the IP has been "
                        "temporarily banned (too many automated requests). Wait for the "
                        "block to expire or run from a different residential IP. "
                        "Debug artifacts saved."
                    )
                raise RuntimeError(
                    "DataDome challenge detected and could not be solved. "
                    "Run on a residential IP or seed the persistent profile with cookies. "
                    "Debug artifacts saved."
                )

        await _handle_cookie_consent(page)

        if await _is_login_needed(page):
            await _fill_login_form(page, email, password)
        else:
            log.info("Already logged in via persistent session")

        if is_datadome_challenge(page):
            resolved = await wait_for_datadome_resolution(page, timeout_ms=8000)
            if not resolved:
                resolved = await solve_datadome_slider(page)
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
