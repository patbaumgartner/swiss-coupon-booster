"""Migros Cumulus stealth login flow.

The Migros login page uses a two-step flow:
  1. Enter e-mail → submit → choose "login with password".
  2. Enter password → submit → redirected to migros.ch.

WebAuthn / FIDO2 prompts are suppressed by injecting a small init script that
removes ``navigator.credentials`` and ``PublicKeyCredential`` from the window
scope.  This forces the password field to appear instead of a passkey dialog.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any

from patchright.async_api import BrowserContext, Page, async_playwright

from browser import (
    create_persistent_context,
    dump_debug_artifacts,
    random_delay,
    serialize_cookies,
)
from config import (
    COOKIE_CONSENT_WAIT_MAX,
    COOKIE_CONSENT_WAIT_MIN,
    INPUT_WAIT_MAX,
    INPUT_WAIT_MIN,
    MIGROS_CF_AUTO_CLICK,
    MIGROS_CF_MANUAL_SOLVE,
    MIGROS_CF_WAIT_MS,
    MIGROS_LOGIN_URL,
    MIGROS_PASSWORD_URL,
    MIGROS_USER_DATA_DIR,
    SEL_MIGROS_COOKIE_ACCEPT,
    SEL_MIGROS_EMAIL,
    SEL_MIGROS_PASSWORD,
    SEL_MIGROS_PASSWORD_LINK,
    SEL_MIGROS_SUBMIT,
    SUBMIT_WAIT_MAX,
    SUBMIT_WAIT_MIN,
    TIMEOUT_MS,
    TYPING_DELAY_MS,
)

log = logging.getLogger("patchright.migros")

_MIGROS_EMAIL_SELECTORS: tuple[str, ...] = (
    SEL_MIGROS_EMAIL,
    "#input-username",
    "input[type='email']",
    "input[autocomplete='username']",
    "input[name='username']",
    "input[name='email']",
    "input[id*='email']",
    "input[id*='user']",
)

_MIGROS_PASSWORD_SELECTORS: tuple[str, ...] = (
    SEL_MIGROS_PASSWORD,
    "input[type='password']",
    "input[name='password']",
    "input[id*='password']",
)

_MIGROS_SUBMIT_SELECTORS: tuple[str, ...] = (
    SEL_MIGROS_SUBMIT,
    "button[type='submit']",
    "button:has-text('Weiter')",
    "button:has-text('Continue')",
    "button:has-text('Fortfahren')",
    "button:has-text('Anmelden')",
    "button:has-text('Login')",
)

_MIGROS_PASSWORD_LINK_SELECTORS: tuple[str, ...] = (
    SEL_MIGROS_PASSWORD_LINK,
    "a:has-text('Mit Passwort anmelden')",
    "button:has-text('Mit Passwort anmelden')",
)

# Injected into every page before any page scripts run.
# Removes WebAuthn APIs so the login page falls back to the password field.
_WEBAUTHN_DISABLE_SCRIPT: str = """
if (window.navigator && window.navigator.credentials) {
    Object.defineProperty(window.navigator, 'credentials', {
        value: undefined, writable: false, configurable: false
    });
}
if (window.PublicKeyCredential) {
    Object.defineProperty(window, 'PublicKeyCredential', {
        value: undefined, writable: false, configurable: false
    });
}
"""


# ── Private helpers ───────────────────────────────────────────────────────────


async def _handle_cookie_consent(page: Page) -> None:
    await random_delay(COOKIE_CONSENT_WAIT_MIN, COOKIE_CONSENT_WAIT_MAX)
    try:
        btn = page.locator(SEL_MIGROS_COOKIE_ACCEPT).first
        await btn.wait_for(timeout=3000)
        if await btn.is_visible():
            await btn.click()
            await page.wait_for_load_state("load")
            log.debug("Accepted cookie consent")
    except Exception:  # noqa: BLE001
        log.debug("Cookie consent not shown or already dismissed")


async def _find_first_visible(page: Page, selectors: tuple[str, ...], timeout_ms: int):
    per_selector_timeout = min(5000, max(1200, timeout_ms // max(len(selectors), 1)))
    for index, selector in enumerate(selectors, start=1):
        locator = page.locator(selector).first
        try:
            await locator.wait_for(timeout=per_selector_timeout)
            if await locator.is_visible():
                log.debug("Using selector at index %d of %d", index, len(selectors))
                return locator
        except Exception:  # noqa: BLE001
            continue
    raise RuntimeError(f"No visible element found for selectors: {selectors}")


async def _is_password_field_visible(page: Page) -> bool:
    for selector in _MIGROS_PASSWORD_SELECTORS:
        locator = page.locator(selector).first
        try:
            if await locator.is_visible():
                return True
        except Exception:  # noqa: BLE001
            continue
    return False


async def _is_visible(page: Page, selector: str) -> bool:
    try:
        return await page.locator(selector).first.is_visible()
    except Exception:  # noqa: BLE001
        return False


async def _is_visible_any(page: Page, selectors: tuple[str, ...]) -> bool:
    for selector in selectors:
        if await _is_visible(page, selector):
            return True
    return False


# ── Cloudflare Turnstile ──────────────────────────────────────────────────────
# login.migros.ch is gated by a Cloudflare "managed challenge" (interactive
# checkbox). The widget lives inside a challenges.cloudflare.com iframe; the
# challenge page title is "Nur einen Moment…" / "Just a moment…".

_CF_TITLE_HINTS: tuple[str, ...] = ("nur einen moment", "just a moment", "einen moment")
_CF_IFRAME_SELECTOR: str = "iframe[src*='challenges.cloudflare.com']"


async def _is_cloudflare_challenge(page: Page) -> bool:
    """Return True when the current page is a Cloudflare Turnstile challenge."""
    try:
        title = (await page.title()).lower()
    except Exception:  # noqa: BLE001
        title = ""
    if any(hint in title for hint in _CF_TITLE_HINTS):
        return True
    try:
        return await page.locator(_CF_IFRAME_SELECTOR).count() > 0
    except Exception:  # noqa: BLE001
        return False


async def _click_turnstile_checkbox(page: Page) -> bool:
    """Best-effort click of the Turnstile checkbox. Returns True if a click landed."""
    iframe = page.locator(_CF_IFRAME_SELECTOR).first
    try:
        await iframe.wait_for(timeout=8000)
    except Exception:  # noqa: BLE001
        return False

    # 1) Reach into the challenge iframe and click the checkbox/label directly.
    frame = page.frame_locator(_CF_IFRAME_SELECTOR)
    for selector in ("input[type='checkbox']", "label", "#challenge-stage", "body"):
        try:
            locator = frame.locator(selector).first
            await locator.wait_for(timeout=2500)
            await locator.click(timeout=2500)
            log.info("Clicked Turnstile element via frame selector %s", selector)
            return True
        except Exception:  # noqa: BLE001
            continue

    # 2) Fall back to clicking the checkbox area via the iframe bounding box.
    try:
        box = await iframe.bounding_box()
        if box:
            x = box["x"] + 30
            y = box["y"] + box["height"] / 2
            await page.mouse.click(x, y)
            log.info("Clicked Turnstile checkbox via bounding-box at (%.0f, %.0f)", x, y)
            return True
    except Exception:  # noqa: BLE001
        pass
    return False


async def _handle_cloudflare_turnstile(page: Page) -> bool:
    """Detect and clear a Cloudflare Turnstile challenge.

    Returns True if there was no challenge or it cleared; False on timeout.
    """
    if not await _is_cloudflare_challenge(page):
        return True

    log.warning("Cloudflare Turnstile challenge detected at %s", page.url)
    deadline = time.monotonic() + MIGROS_CF_WAIT_MS / 1000.0
    click_attempts = 0
    while time.monotonic() < deadline:
        if not await _is_cloudflare_challenge(page):
            log.info("Cloudflare challenge cleared ✓")
            try:
                await page.wait_for_load_state("load")
            except Exception:  # noqa: BLE001
                pass
            return True
        if MIGROS_CF_AUTO_CLICK and click_attempts < 3:
            if await _click_turnstile_checkbox(page):
                click_attempts += 1
                await asyncio.sleep(3)
                continue
        if MIGROS_CF_MANUAL_SOLVE:
            log.warning("Waiting for MANUAL Turnstile solve in the headed window…")
        await asyncio.sleep(2)

    log.error("Cloudflare Turnstile challenge did NOT clear within %d ms", MIGROS_CF_WAIT_MS)
    return False


async def _switch_to_password_mode_if_available(page: Page) -> None:
    for index, selector in enumerate(_MIGROS_PASSWORD_LINK_SELECTORS, start=1):
        locator = page.locator(selector).first
        try:
            await locator.wait_for(timeout=4000)
            if await locator.is_visible():
                log.debug(
                    "Switching to password mode via selector candidate %d of %d",
                    index,
                    len(_MIGROS_PASSWORD_LINK_SELECTORS),
                )
                await locator.click()
                await page.wait_for_load_state("load")
                return
        except Exception:  # noqa: BLE001
            continue


async def _advance_from_email_to_password(page: Page, email: str) -> bool:
    """Get from the e-mail form to the password field.

    Tolerates the Cloudflare pattern where submitting the e-mail triggers a
    challenge that, once cleared, bounces back to an empty e-mail form (losing
    the submission). Re-submitting after cf_clearance is set usually reaches the
    password page. Returns True if the password field becomes visible.
    """
    for attempt in range(1, 4):
        await _handle_cloudflare_turnstile(page)

        if await _is_password_field_visible(page):
            return True

        # A passkey / login-options page may need switching to password mode.
        await _switch_to_password_mode_if_available(page)
        if await _is_password_field_visible(page):
            return True

        if not await _is_visible_any(page, _MIGROS_EMAIL_SELECTORS):
            # Neither password nor e-mail field present yet — let the page settle.
            await asyncio.sleep(2)
            continue

        log.debug("E-mail submit attempt %d of 3", attempt)
        await random_delay(INPUT_WAIT_MIN, INPUT_WAIT_MAX)
        email_field = await _find_first_visible(page, _MIGROS_EMAIL_SELECTORS, TIMEOUT_MS)
        await email_field.clear()
        await email_field.press_sequentially(email, delay=TYPING_DELAY_MS)

        await random_delay(SUBMIT_WAIT_MIN, SUBMIT_WAIT_MAX)
        submit_btn = await _find_first_visible(page, _MIGROS_SUBMIT_SELECTORS, TIMEOUT_MS)
        await submit_btn.click()
        try:
            await page.wait_for_load_state("load")
        except Exception:  # noqa: BLE001
            pass
        # Give Cloudflare / navigation a moment before the next check.
        await asyncio.sleep(2)

    return await _is_password_field_visible(page)


async def _run_login_flow(context: BrowserContext, email: str, password: str) -> dict[str, Any]:
    page = await context.new_page()
    try:
        user_agent: str = await page.evaluate("() => navigator.userAgent")
        language: str = await page.evaluate("() => navigator.language")
        log.debug("User-agent: %s", user_agent)

        log.info("Navigating to Migros login: %s", MIGROS_LOGIN_URL)
        await page.goto(MIGROS_LOGIN_URL)
        await page.wait_for_load_state("load")
        await random_delay(1000, 2000)

        await _handle_cookie_consent(page)

        # Migros gates the login behind a Cloudflare Turnstile challenge.
        await _handle_cloudflare_turnstile(page)

        # ── Already authenticated? ────────────────────────────────────────────
        # If the persistent profile holds a valid session, login.migros.ch
        # redirects immediately to account.migros.ch (or similar).  No login
        # form is shown, so we can simply collect the cookies and return.
        if "login.migros.ch" not in page.url:
            log.info("Already authenticated — redirected to %s; reusing session", page.url)
            cookies_raw = await context.cookies()
            log.info("Collected %d session cookies (reused session)", len(cookies_raw))
            return {"cookies": serialize_cookies(cookies_raw), "userAgent": user_agent, "language": language}
        # ─────────────────────────────────────────────────────────────────────

        # Migros can open directly on passkey mode where no email field is present.
        # In that case switch to password mode first.
        if "/login/passkey" in page.url or await _is_visible(page, SEL_MIGROS_PASSWORD_LINK):
            log.debug("Passkey page detected; switching to password login")
            await _switch_to_password_mode_if_available(page)

            try:
                await page.wait_for_url(f"{MIGROS_PASSWORD_URL}*", timeout=TIMEOUT_MS)
                log.debug("Switched to password page")
            except Exception:  # noqa: BLE001
                log.warning("Timed out waiting for password page after passkey switch; continuing at %s", page.url)

        # Some variants reveal the password option with a slight delay.
        if not await _is_password_field_visible(page):
            await _switch_to_password_mode_if_available(page)

        # Step 1+2: advance from the e-mail form to the password field. This
        # tolerates Cloudflare challenges that bounce the form back to an empty
        # state — re-submitting once cf_clearance is set usually reaches the
        # password page.
        if not await _is_password_field_visible(page):
            if not await _advance_from_email_to_password(page, email):
                log.warning(
                    "Password field not reached after retries; navigating directly to %s",
                    MIGROS_PASSWORD_URL,
                )
                await page.goto(MIGROS_PASSWORD_URL)
                await page.wait_for_load_state("load")
                await _handle_cloudflare_turnstile(page)
                await _switch_to_password_mode_if_available(page)

        try:
            await page.wait_for_url(f"{MIGROS_PASSWORD_URL}*", timeout=TIMEOUT_MS)
            log.debug("On password page")
        except Exception:  # noqa: BLE001
            log.warning("Timed out waiting for password page; continuing at %s", page.url)

        # Step 3: enter password and submit
        log.debug("Entering password")
        await random_delay(INPUT_WAIT_MIN, INPUT_WAIT_MAX)
        pwd_field = await _find_first_visible(page, _MIGROS_PASSWORD_SELECTORS, TIMEOUT_MS)
        await pwd_field.clear()
        await pwd_field.press_sequentially(password, delay=TYPING_DELAY_MS)

        await random_delay(SUBMIT_WAIT_MIN, SUBMIT_WAIT_MAX)
        submit_btn2 = await _find_first_visible(page, _MIGROS_SUBMIT_SELECTORS, TIMEOUT_MS)
        await submit_btn2.click()
        await page.wait_for_load_state("load")

        if "login.migros.ch" in page.url:
            await dump_debug_artifacts(page, "migros_login_error")
            raise RuntimeError(f"Migros authentication failed — still on login page: {page.url}")

        log.info("Login successful, final URL: %s", page.url)

        cookies_raw = await context.cookies()
        log.info("Collected %d session cookies", len(cookies_raw))
        return {"cookies": serialize_cookies(cookies_raw), "userAgent": user_agent, "language": language}

    except Exception:
        await dump_debug_artifacts(page, "migros_login_error")
        raise
    finally:
        await page.close()


# ── Public API ────────────────────────────────────────────────────────────────


async def migros_stealth_login(email: str, password: str) -> dict[str, Any]:
    """Perform a stealth Migros Cumulus login using Patchright.

    Returns:
        A dict with keys ``cookies`` (list), ``userAgent`` (str), ``language`` (str).

    Raises:
        RuntimeError: on login failure.
    """
    MIGROS_USER_DATA_DIR.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as pw:
        context = await create_persistent_context(
            pw,
            MIGROS_USER_DATA_DIR,
            init_script=_WEBAUTHN_DISABLE_SCRIPT,
        )
        try:
            return await _run_login_flow(context, email, password)
        finally:
            await context.close()
