"""Shared Patchright / Chromium browser utilities.

All login flows (Coop and Migros) share the same browser launch configuration,
DataDome detection helpers, and debug artifact tooling defined here.
"""

from __future__ import annotations

import asyncio
import logging
import random
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from patchright.async_api import BrowserContext, Page, Playwright

from config import (
    HEADLESS,
    PROXY_URL,
    SCREENSHOT_DIR,
    SLOW_MO_MS,
    TIMEOUT_MS,
)

log = logging.getLogger("patchright.browser")


# ── Timing helpers ────────────────────────────────────────────────────────────


async def random_delay(min_ms: int, max_ms: int) -> None:
    """Sleep for a random duration in [min_ms, max_ms] to simulate human behaviour."""
    delay_ms = random.randint(min_ms, max_ms)
    log.debug("Waiting %d ms", delay_ms)
    await asyncio.sleep(delay_ms / 1000.0)


# ── DataDome detection ────────────────────────────────────────────────────────


def is_datadome_challenge(page: Page) -> bool:
    """Return True if the current page is a DataDome challenge."""
    url = page.url
    if _is_datadome_url(url):
        log.warning("DataDome challenge detected via URL: %s", url)
        return True
    for frame in page.frames:
        if _is_datadome_url(frame.url):
            log.warning("DataDome challenge detected via iframe: %s", frame.url)
            return True
    return False


def _is_datadome_url(url: str) -> bool:
    """Return True when URL host is captcha-delivery.com or one of its subdomains."""
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        return False

    return host == "captcha-delivery.com" or host.endswith(".captcha-delivery.com")


# Markers shown on DataDome's terminal "hard block" page (not a solvable
# challenge — the IP/session has been banned and must cool down or change).
_HARD_BLOCK_MARKERS = (
    "Der Zugriff ist vorübergehend eingeschränkt",
    "Access is temporarily restricted",
    "übermenschlicher Geschwindigkeit",
    "superhuman speed",
)


async def is_datadome_hard_block(page: Page) -> bool:
    """Return True if DataDome is serving a terminal block page (IP banned).

    This is distinct from a solvable slider/verification challenge: no
    browser-side automation can clear it — the IP must cool down or change.
    """
    for frame in page.frames:
        if not _is_datadome_url(frame.url):
            continue
        try:
            text = await frame.locator("body").inner_text(timeout=1500)
        except Exception:  # noqa: BLE001
            continue
        if any(marker in text for marker in _HARD_BLOCK_MARKERS):
            return True
    return False


async def wait_for_datadome_resolution(page: Page, timeout_ms: int = 30000) -> bool:
    """
    Wait for a DataDome browser-verification (t=bv) challenge to auto-resolve.

    Patchright patches Chromium at the C++ level so DataDome's JS fingerprinting
    passes automatically.  Returns True when the challenge disappears, False on
    timeout.
    """
    log.info(
        "DataDome browser-verification challenge — waiting up to %d ms for auto-resolution…",
        timeout_ms,
    )
    deadline = time.monotonic() + timeout_ms / 1000.0
    poll_interval = 1.0
    while time.monotonic() < deadline:
        await asyncio.sleep(poll_interval)
        if not is_datadome_challenge(page):
            log.info("DataDome challenge resolved ✓")
            return True
        log.debug("Challenge still active, %.0f s remaining…", max(deadline - time.monotonic(), 0))
    log.warning("DataDome challenge did NOT auto-resolve within %d ms", timeout_ms)
    return False


# ── DataDome slider (t=fe) solver ─────────────────────────────────────────────

# Handle element inside the captcha iframe (DataDome ships a few variants).
_SLIDER_HANDLE_SELECTORS = (".slider", ".sliderIcon", ".slider-icon")
_SLIDER_CONTAINER_SELECTORS = (".sliderContainer", ".slidercontainer")
_DATADOME_IFRAME = 'iframe[src*="captcha-delivery.com"]'


async def _cdp_human_drag(page: Page, x0: float, y0: float, x1: float) -> None:
    """Drag from (x0, y0) to (x1, y0) with a human momentum profile.

    Uses a raw CDP session so the trajectory is smooth regardless of the
    context ``slow_mo`` setting. Playwright's ``mouse.move`` also dispatches
    ``Input.dispatchMouseEvent`` under the hood — the only difference is that
    ``slow_mo`` inserts a pause before every call, turning the drag into a
    series of teleport-then-wait jumps that DataDome rejects. Here we control
    the timing ourselves for a realistic ease-in-out slide.
    """
    cdp = await page.context.new_cdp_session(page)

    async def move(x: float, y: float, buttons: int = 0) -> None:
        await cdp.send(
            "Input.dispatchMouseEvent",
            {"type": "mouseMoved", "x": x, "y": y, "buttons": buttons},
        )

    try:
        # Curved approach, then grab the handle.
        await move(x0 - 20, y0 - 12)
        await asyncio.sleep(random.uniform(0.09, 0.18))
        await move(x0, y0)
        await asyncio.sleep(random.uniform(0.14, 0.28))
        await cdp.send(
            "Input.dispatchMouseEvent",
            {"type": "mousePressed", "x": x0, "y": y0, "button": "left", "buttons": 1},
        )
        await asyncio.sleep(random.uniform(0.09, 0.19))

        dist = x1 - x0
        steps = random.randint(45, 65)
        cur_x = x0
        for i in range(1, steps + 1):
            t = i / steps
            ease = t * t * (3 - 2 * t)  # smoothstep ease-in-out
            target_x = x0 + dist * ease
            cur_x += (target_x - cur_x) * random.uniform(0.6, 0.95)
            y = y0 + random.uniform(-1.8, 1.8)
            await move(cur_x, y, buttons=1)
            await asyncio.sleep(random.uniform(0.008, 0.024))

        # Settle exactly on target with tiny corrections.
        await move(x1 - random.uniform(1, 3), y0, buttons=1)
        await asyncio.sleep(random.uniform(0.06, 0.13))
        await move(x1, y0, buttons=1)
        await asyncio.sleep(random.uniform(0.12, 0.22))
        await cdp.send(
            "Input.dispatchMouseEvent",
            {"type": "mouseReleased", "x": x1, "y": y0, "button": "left", "buttons": 0},
        )
    finally:
        await cdp.detach()


async def cdp_human_click(page: Page, x: float, y: float) -> None:
    """Move to (x, y) with a human trajectory and click, via trusted CDP events.

    Cloudflare's Turnstile rejects Playwright's ``locator.click`` (it lands as an
    instantaneous, motion-less event). Driving ``Input.dispatchMouseEvent`` with
    a curved approach + realistic press/release timing produces a trusted click
    with genuine pointer movement, which is what Turnstile expects.
    """
    cdp = await page.context.new_cdp_session(page)

    async def move(mx: float, my: float) -> None:
        await cdp.send("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": mx, "y": my})

    try:
        # Curved approach from up-and-left of the target.
        start_x = x - random.uniform(60, 110)
        start_y = y - random.uniform(35, 70)
        steps = random.randint(22, 34)
        for i in range(1, steps + 1):
            t = i / steps
            ease = t * t * (3 - 2 * t)  # smoothstep ease-in-out
            mx = start_x + (x - start_x) * ease + random.uniform(-1.2, 1.2)
            my = start_y + (y - start_y) * ease + random.uniform(-1.2, 1.2)
            await move(mx, my)
            await asyncio.sleep(random.uniform(0.008, 0.022))

        # Settle on the target, small pause, then a realistic press/release.
        await move(x, y)
        await asyncio.sleep(random.uniform(0.10, 0.24))
        await cdp.send(
            "Input.dispatchMouseEvent",
            {"type": "mousePressed", "x": x, "y": y, "button": "left", "buttons": 1, "clickCount": 1},
        )
        await asyncio.sleep(random.uniform(0.05, 0.13))
        await cdp.send(
            "Input.dispatchMouseEvent",
            {"type": "mouseReleased", "x": x, "y": y, "button": "left", "buttons": 0, "clickCount": 1},
        )
    finally:
        await cdp.detach()


async def _find_slider_handle(page: Page) -> dict[str, float] | None:
    """Return the bounding box (page coords) of the slider handle, or None."""
    frame = page.frame_locator(_DATADOME_IFRAME)
    for sel in _SLIDER_HANDLE_SELECTORS:
        loc = frame.locator(sel).first
        try:
            await loc.wait_for(state="visible", timeout=4000)
            box = await loc.bounding_box()
            if box and box["width"] > 0:
                log.debug("Slider handle found via %s: %s", sel, box)
                return box
        except Exception:  # noqa: BLE001
            continue
    return None


async def _find_slider_travel(page: Page, handle: dict[str, float]) -> float:
    """Return the target x (page coords) to slide the handle to (right edge)."""
    frame = page.frame_locator(_DATADOME_IFRAME)
    for sel in _SLIDER_CONTAINER_SELECTORS:
        loc = frame.locator(sel).first
        try:
            box = await loc.bounding_box()
            if box and box["width"] > 0:
                return box["x"] + box["width"] - handle["width"] / 2
        except Exception:  # noqa: BLE001
            continue
    # Fallback: slide a fixed generous distance to the right.
    return handle["x"] + 260


async def solve_datadome_slider(page: Page, max_attempts: int = 3) -> bool:
    """Attempt to solve a DataDome slider ("t=fe") challenge.

    Returns True when the challenge disappears, False otherwise.
    """
    for attempt in range(1, max_attempts + 1):
        if await is_datadome_hard_block(page):
            log.error(
                "DataDome served a terminal block page (IP banned) — no slider to solve. "
                "The IP must cool down or change."
            )
            return False
        handle = await _find_slider_handle(page)
        if not handle:
            log.warning("Slider attempt %d: handle not found, waiting for reload…", attempt)
            await asyncio.sleep(2.5)
            if await is_datadome_hard_block(page):
                log.error("DataDome escalated to a terminal block page (IP banned).")
                return False
            handle = await _find_slider_handle(page)
            if not handle:
                continue

        x0 = handle["x"] + handle["width"] / 2
        y0 = handle["y"] + handle["height"] / 2
        x1 = await _find_slider_travel(page, handle)
        log.info("Slider attempt %d: dragging %.0f → %.0f @ y=%.0f", attempt, x0, x1, y0)
        await _cdp_human_drag(page, x0, y0, x1)
        await asyncio.sleep(1.8)

        if not is_datadome_challenge(page):
            log.info("DataDome slider solved on attempt %d ✓", attempt)
            return True
        log.warning("Slider attempt %d did not clear the challenge", attempt)
        # Give DataDome time to render the reloaded challenge before retrying.
        await asyncio.sleep(random.uniform(2.0, 3.5))

    log.warning("DataDome slider not solved after %d attempts", max_attempts)
    return False


# ── Debug artifacts ───────────────────────────────────────────────────────────


async def dump_debug_artifacts(page: Page, label: str) -> None:
    """Save a full-page screenshot and HTML dump for post-mortem debugging."""
    try:
        SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
        ts = int(time.time())
        screenshot_path = SCREENSHOT_DIR / f"{label}_{ts}.png"
        html_path = SCREENSHOT_DIR / f"{label}_{ts}.html"
        await page.screenshot(path=str(screenshot_path), full_page=True)
        html_path.write_text(await page.content(), encoding="utf-8")
        log.info("Debug artifacts saved: %s, %s", screenshot_path, html_path)
    except Exception as exc:  # noqa: BLE001
        log.warning("Could not save debug artifacts: %s", exc)


# ── Browser / context factory ─────────────────────────────────────────────────


def build_launch_options() -> dict[str, Any]:
    """Return Patchright launch options.

    IMPORTANT: --disable-blink-features=AutomationControlled is required to keep
    navigator.webdriver === false. Because Chromium is driven over the CDP pipe,
    webdriver stays true unless this flag is set. Patchright is supposed to add
    it automatically, but on the launch_persistent_context path it does not, so
    we set it explicitly here. Do NOT add other flags that reveal automation.
    """
    opts: dict[str, Any] = {
        "headless": HEADLESS,
        "slow_mo": SLOW_MO_MS,
        "args": [
            "--disable-blink-features=AutomationControlled",
            "--disable-dev-shm-usage",
            "--no-first-run",
            "--no-default-browser-check",
            "--window-size=1920,1080",
            "--window-position=0,0",
        ],
    }
    if PROXY_URL:
        opts["proxy"] = {"server": PROXY_URL}
        log.info("Using proxy: %s", PROXY_URL)
    return opts


def build_context_options() -> dict[str, Any]:
    """Return persistent-context options that mimic a typical Swiss desktop user."""
    opts: dict[str, Any] = {
        "viewport": {"width": 1920, "height": 1080},
        "locale": "de-CH",
        "timezone_id": "Europe/Zurich",
        "permissions": ["geolocation", "notifications"],
        "geolocation": {"latitude": 47.3769, "longitude": 8.5417},
        "device_scale_factor": 1.0,
        "is_mobile": False,
        "has_touch": False,
        "color_scheme": "light",
    }
    if PROXY_URL:
        opts["proxy"] = {"server": PROXY_URL}
    return opts


async def create_persistent_context(
    pw: Playwright,
    user_data_dir: Path,
    *,
    init_script: str | None = None,
) -> BrowserContext:
    """Launch a persistent Chromium context with the shared stealth configuration.

    Args:
        pw: The active Playwright instance.
        user_data_dir: Directory for persistent browser profile (cookies, storage).
        init_script: Optional JavaScript injected into every page before any
            page scripts run.  Used by the Migros flow to disable WebAuthn.
    """
    persistent_opts = {**build_launch_options(), **build_context_options()}
    log.info("Launching persistent Chromium context (user-data: %s)", user_data_dir)
    context: BrowserContext = await pw.chromium.launch_persistent_context(
        str(user_data_dir),
        **persistent_opts,
    )
    if init_script:
        await context.add_init_script(init_script)
    return context


# ── WebAuthn virtual authenticator ────────────────────────────────────────────
# Chromium's CDP WebAuthn domain lets us attach a software authenticator that
# holds a passkey's private key (unlike hardware authenticators, virtual ones
# expose the key so it can be exported and re-imported). This is how the Migros
# passkey login is automated: register once, export the credential, then import
# it before each login so the passkey challenge is signed automatically.

_VIRTUAL_AUTHENTICATOR_OPTIONS: dict[str, Any] = {
    "protocol": "ctap2",
    "transport": "internal",
    "hasResidentKey": True,
    "hasUserVerification": True,
    "isUserVerified": True,
    "automaticPresenceSimulation": True,
}


async def enable_virtual_authenticator(cdp: Any) -> str:
    """Enable WebAuthn and attach a virtual platform authenticator.

    Args:
        cdp: A CDP session (``context.new_cdp_session(page)``).

    Returns:
        The authenticator id, needed for add/get credential calls.
    """
    await cdp.send("WebAuthn.enable", {"enableUI": False})
    result = await cdp.send(
        "WebAuthn.addVirtualAuthenticator",
        {"options": _VIRTUAL_AUTHENTICATOR_OPTIONS},
    )
    authenticator_id = result["authenticatorId"]
    log.info("Virtual authenticator attached: %s", authenticator_id)
    return authenticator_id


async def get_virtual_credentials(cdp: Any, authenticator_id: str) -> list[dict[str, Any]]:
    """Return the credentials currently held by the virtual authenticator."""
    result = await cdp.send("WebAuthn.getCredentials", {"authenticatorId": authenticator_id})
    return result.get("credentials", [])


async def import_virtual_credential(cdp: Any, authenticator_id: str, credential: dict[str, Any]) -> None:
    """Import a previously-exported passkey credential into the authenticator."""
    await cdp.send(
        "WebAuthn.addCredential",
        {
            "authenticatorId": authenticator_id,
            "credential": {
                "credentialId": credential["credentialId"],
                "isResidentCredential": credential.get("isResidentCredential", True),
                "rpId": credential["rpId"],
                "privateKey": credential["privateKey"],
                "userHandle": credential.get("userHandle"),
                "signCount": credential.get("signCount", 0),
            },
        },
    )
    log.info("Imported passkey credential for rpId=%s", credential.get("rpId"))


# ── Cookie serialization ──────────────────────────────────────────────────────


def serialize_cookies(cookies_raw: list[Any]) -> list[dict[str, Any]]:
    """Convert Patchright TypedDict cookies to plain JSON-serialisable dicts."""
    return [
        {
            "name": c["name"],
            "value": c["value"],
            "domain": c.get("domain") or None,
            "path": c.get("path") or None,
            "expires": c.get("expires", -1),
            "httpOnly": c.get("httpOnly", False),
            "secure": c.get("secure", False),
            "sameSite": c.get("sameSite", "None"),
        }
        for c in cookies_raw
    ]
