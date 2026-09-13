"""Tests for POST /login/coop (Coop Supercard)."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

# ── Input validation ──────────────────────────────────────────────────────────


def test_coop_login_missing_fields(client):
    response = client.post("/login/coop", json={})
    assert response.status_code == 422


def test_coop_login_missing_password(client):
    response = client.post("/login/coop", json={"email": "user@example.com"})
    assert response.status_code == 422


def test_coop_login_invalid_email(client):
    response = client.post("/login/coop", json={"email": "not-an-email", "password": "secret"})
    assert response.status_code == 422


# ── Success path ──────────────────────────────────────────────────────────────


@pytest.mark.anyio
async def test_coop_login_success(client):
    """Verify /login/coop returns the sidecar result when coop_stealth_login succeeds."""
    fake_result = {
        "cookies": [
            {
                "name": "datadome",
                "value": "abc",
                "domain": ".supercard.ch",
                "path": "/",
                "expires": -1,
                "httpOnly": False,
                "secure": True,
                "sameSite": "None",
            },
            {
                "name": "session_id",
                "value": "xyz789",
                "domain": ".supercard.ch",
                "path": "/",
                "expires": 9999999999.0,
                "httpOnly": True,
                "secure": True,
                "sameSite": "Lax",
            },
        ],
        "userAgent": "Mozilla/5.0 (Test)",
        "language": "de-CH",
    }

    with patch("main.coop_stealth_login", new_callable=AsyncMock, return_value=fake_result):
        response = client.post("/login/coop", json={"email": "user@example.com", "password": "pass"})

    assert response.status_code == 200
    data = response.json()
    assert len(data["cookies"]) == 2
    assert data["cookies"][0]["name"] == "datadome"
    assert data["cookies"][1]["name"] == "session_id"
    assert data["userAgent"] == "Mozilla/5.0 (Test)"
    assert data["language"] == "de-CH"


# ── Error paths ───────────────────────────────────────────────────────────────


@pytest.mark.anyio
async def test_coop_login_datadome_challenge_returns_503(client):
    """Verify /login/coop returns 503 when a DataDome challenge is detected."""
    with patch(
        "main.coop_stealth_login",
        new_callable=AsyncMock,
        side_effect=RuntimeError("DataDome challenge detected and did not auto-resolve"),
    ):
        response = client.post("/login/coop", json={"email": "user@example.com", "password": "pass"})

    assert response.status_code == 503
    assert "DataDome" in response.json()["detail"]


@pytest.mark.anyio
async def test_coop_login_unexpected_error_returns_500(client):
    """Verify /login/coop returns 500 on an unexpected internal error."""
    with patch(
        "main.coop_stealth_login",
        new_callable=AsyncMock,
        side_effect=Exception("Something unexpected"),
    ):
        response = client.post("/login/coop", json={"email": "user@example.com", "password": "pass"})

    assert response.status_code == 500
    assert "Internal error" in response.json()["detail"]


# ── Login flow ────────────────────────────────────────────────────────────────


def _fake_page(url: str = "https://www.supercard.ch/de.html?sso-check=1") -> MagicMock:
    """A page whose locators never resolve (no login link, no consent banner)."""
    page = MagicMock()
    page.url = url
    page.frames = []
    page.evaluate = AsyncMock(side_effect=["Mozilla/5.0 (Test)", "de-CH"])
    page.goto = AsyncMock()
    page.close = AsyncMock()
    page.screenshot = AsyncMock()
    page.content = AsyncMock(return_value="<html></html>")
    locator = MagicMock()
    locator.first = locator
    locator.wait_for = AsyncMock(side_effect=TimeoutError("not found"))
    locator.is_visible = AsyncMock(return_value=False)
    page.locator.return_value = locator
    return page


@pytest.mark.anyio
async def test_login_flow_fails_closed_when_login_link_is_missing(monkeypatch, tmp_path):
    """Cookies are cleared before navigating, so a missing login link must raise, not
    silently return the anonymous cookies (supercard.ch redesign, 2026-09-10)."""
    import coop

    page = _fake_page()
    context = MagicMock()
    context.new_page = AsyncMock(return_value=page)
    context.cookies = AsyncMock(return_value=[{"name": "datadome", "value": "x"}])
    context.clear_cookies = AsyncMock()
    context.add_cookies = AsyncMock()
    monkeypatch.setattr(coop, "random_delay", AsyncMock())
    monkeypatch.setattr("browser.SCREENSHOT_DIR", tmp_path)

    with pytest.raises(RuntimeError, match="login link not found"):
        await coop._run_login_flow(context, "user@example.com", "pass")

    assert any(p.name.startswith("coop_login_link_missing_") for p in tmp_path.iterdir())
    page.close.assert_awaited_once()
