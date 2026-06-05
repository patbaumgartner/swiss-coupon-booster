"""Tests for POST /login/coop (Coop Supercard)."""

from unittest.mock import AsyncMock, patch

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
