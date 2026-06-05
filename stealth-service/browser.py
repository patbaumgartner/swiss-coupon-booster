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

from patchright.async_api import BrowserContext, Page, Playwright

from config import (
    HEADLESS,
    PROXY_URL,
    SCREENSHOT_DIR,
    SLOW_MO_MS,
    TIMEOUT_MS,
)

log = logging.getLogger("stealth-service.browser")


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
    if "captcha-delivery.com" in url or "geo.captcha-delivery.com" in url:
        log.warning("DataDome challenge detected via URL: %s", url)
        return True
    for frame in page.frames:
        if "captcha-delivery.com" in frame.url:
            log.warning("DataDome challenge detected via iframe: %s", frame.url)
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

    IMPORTANT: Do NOT add --disable-blink-features=AutomationControlled or any
    other flag that reveals automation — those undo Patchright's stealth patches.
    """
    opts: dict[str, Any] = {
        "headless": HEADLESS,
        "slow_mo": SLOW_MO_MS,
        "args": [
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


# ── Cookie serialization ──────────────────────────────────────────────────────


def serialize_cookies(cookies_raw: list[Any]) -> list[dict[str, Any]]:
    """Convert Patchright TypedDict cookies to plain JSON-serialisable dicts."""
    return [
        {
            "name": c["name"],
            "value": c["value"],
            "domain": c.get("domain", ""),
            "path": c.get("path", "/"),
            "expires": c.get("expires", -1),
            "httpOnly": c.get("httpOnly", False),
            "secure": c.get("secure", False),
            "sameSite": c.get("sameSite", "None"),
        }
        for c in cookies_raw
    ]
