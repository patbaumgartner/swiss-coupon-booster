"""Environment-variable configuration for the patchright.

All tuneable parameters are read from environment variables so that the
service can be configured at container runtime without rebuilding the image.
"""

from __future__ import annotations

import os
from pathlib import Path

# ── Logging ───────────────────────────────────────────────────────────────────
LOG_LEVEL: str = os.getenv("LOG_LEVEL", "info")

# ── Browser behaviour ─────────────────────────────────────────────────────────
# HEADLESS is always False inside the container (Xvfb virtual display).
HEADLESS: bool = os.getenv("HEADLESS", "true").lower() == "true"
SLOW_MO_MS: int = int(os.getenv("SLOW_MO_MS", "800"))
TIMEOUT_MS: int = int(os.getenv("TIMEOUT_MS", "25000"))
TYPING_DELAY_MS: int = int(os.getenv("TYPING_DELAY_MS", "80"))

# ── Network ───────────────────────────────────────────────────────────────────
# Format: "http://user:pass@host:port" or "socks5://user:pass@host:port"
PROXY_URL: str | None = os.getenv("PROXY_URL")

# ── Storage ───────────────────────────────────────────────────────────────────
SCREENSHOT_DIR: Path = Path(os.getenv("SCREENSHOT_DIR", "/data/screenshots"))

# ── Human-behaviour timing (milliseconds) ────────────────────────────────────
# These random delays make browser automation indistinguishable from a human.
DATADOME_WAIT_MIN: int = int(os.getenv("DATADOME_WAIT_MIN_MS", "3000"))
DATADOME_WAIT_MAX: int = int(os.getenv("DATADOME_WAIT_MAX_MS", "5000"))
COOKIE_CONSENT_WAIT_MIN: int = int(os.getenv("COOKIE_CONSENT_WAIT_MIN_MS", "500"))
COOKIE_CONSENT_WAIT_MAX: int = int(os.getenv("COOKIE_CONSENT_WAIT_MAX_MS", "1500"))
LOGIN_CLICK_WAIT_MIN: int = int(os.getenv("LOGIN_CLICK_WAIT_MIN_MS", "300"))
LOGIN_CLICK_WAIT_MAX: int = int(os.getenv("LOGIN_CLICK_WAIT_MAX_MS", "800"))
INPUT_WAIT_MIN: int = int(os.getenv("INPUT_WAIT_MIN_MS", "400"))
INPUT_WAIT_MAX: int = int(os.getenv("INPUT_WAIT_MAX_MS", "1200"))
SUBMIT_WAIT_MIN: int = int(os.getenv("SUBMIT_WAIT_MIN_MS", "300"))
SUBMIT_WAIT_MAX: int = int(os.getenv("SUBMIT_WAIT_MAX_MS", "700"))

# ── Coop Supercard ────────────────────────────────────────────────────────────
COOP_USER_DATA_DIR: Path = Path(os.getenv("COOP_USER_DATA_DIR", "/data/coop-user-data"))
COOP_LOGIN_URL: str = os.getenv(
    "COOP_LOGIN_URL",
    "https://www.supercard.ch/content/supercard/de.html?sso-check=1",
)

# Selectors — must match CoopSelectorsProperties in the Java application.
SEL_COOP_LOGIN_LINK: str = os.getenv("SEL_LOGIN_LINK", "#a-accountLogin-desktop")
SEL_COOP_USERNAME: str = os.getenv("SEL_USERNAME", "#username")
SEL_COOP_PASSWORD: str = os.getenv("SEL_PASSWORD", "#password")
SEL_COOP_SUBMIT: str = os.getenv("SEL_SUBMIT", "#btnSubmit")
SEL_COOP_COOKIE_ACCEPT: str = os.getenv("SEL_COOKIE_ACCEPT", '[data-testid="uc-accept-all-button"]')

# ── Migros Cumulus ────────────────────────────────────────────────────────────
MIGROS_USER_DATA_DIR: Path = Path(os.getenv("MIGROS_USER_DATA_DIR", "/data/migros-user-data"))
MIGROS_LOGIN_URL: str = os.getenv("MIGROS_LOGIN_URL", "https://login.migros.ch/")
MIGROS_PASSWORD_URL: str = os.getenv("MIGROS_PASSWORD_URL", "https://login.migros.ch/login/password")

# Selectors — must match MigrosSelectorsProperties in the Java application.
SEL_MIGROS_EMAIL: str = os.getenv("SEL_MIGROS_EMAIL", "#input-email")
SEL_MIGROS_PASSWORD: str = os.getenv("SEL_MIGROS_PASSWORD", "#input-password")
SEL_MIGROS_SUBMIT: str = os.getenv("SEL_MIGROS_SUBMIT", "#button-submit")
SEL_MIGROS_PASSWORD_LINK: str = os.getenv("SEL_MIGROS_PASSWORD_LINK", "#link-login-option-PASSWORD")
SEL_MIGROS_COOKIE_ACCEPT: str = os.getenv(
    "SEL_MIGROS_COOKIE_ACCEPT",
    "button:has-text('Akzeptieren'), button:has-text('Accept')",
)

# Cloudflare Turnstile handling on the Migros login page (login.migros.ch).
# Migros gates its login behind a Cloudflare "managed challenge" (interactive
# checkbox). MIGROS_CF_AUTO_CLICK lets the service click the checkbox itself;
# MIGROS_CF_MANUAL_SOLVE keeps it waiting so a human can solve it in a headed
# window (useful for seeding cf_clearance); MIGROS_CF_WAIT_MS bounds the wait.
MIGROS_CF_AUTO_CLICK: bool = os.getenv("MIGROS_CF_AUTO_CLICK", "true").lower() == "true"
MIGROS_CF_MANUAL_SOLVE: bool = os.getenv("MIGROS_CF_MANUAL_SOLVE", "false").lower() == "true"
MIGROS_CF_WAIT_MS: int = int(os.getenv("MIGROS_CF_WAIT_MS", "45000"))
