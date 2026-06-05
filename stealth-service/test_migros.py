"""Tests for POST /login/migros (Migros Cumulus)."""

from unittest.mock import AsyncMock, patch

import pytest


# ── Input validation ──────────────────────────────────────────────────────────


def test_migros_login_missing_fields(client):
    response = client.post("/login/migros", json={})
    assert response.status_code == 422


def test_migros_login_missing_password(client):
    response = client.post("/login/migros", json={"email": "user@example.com"})
    assert response.status_code == 422


def test_migros_login_invalid_email(client):
    response = client.post("/login/migros", json={"email": "not-an-email", "password": "secret"})
    assert response.status_code == 422


# ── Success path ──────────────────────────────────────────────────────────────


@pytest.mark.anyio
async def test_migros_login_success(client):
    """Verify /login/migros returns the sidecar result when migros_stealth_login succeeds."""
    fake_result = {
        "cookies": [
            {
                "name": "m-session",
                "value": "tok123",
                "domain": ".migros.ch",
                "path": "/",
                "expires": 9999999999.0,
                "httpOnly": True,
                "secure": True,
                "sameSite": "Strict",
            },
            {
                "name": "cumulus-id",
                "value": "cum456",
                "domain": "account.migros.ch",
                "path": "/",
                "expires": -1,
                "httpOnly": False,
                "secure": True,
                "sameSite": "None",
            },
        ],
        "userAgent": "Mozilla/5.0 (MigrosTest)",
        "language": "de-CH",
    }

    with patch("main.migros_stealth_login", new_callable=AsyncMock, return_value=fake_result):
        response = client.post("/login/migros", json={"email": "user@example.com", "password": "pass"})

    assert response.status_code == 200
    data = response.json()
    assert len(data["cookies"]) == 2
    assert data["cookies"][0]["name"] == "m-session"
    assert data["cookies"][1]["name"] == "cumulus-id"
    assert data["userAgent"] == "Mozilla/5.0 (MigrosTest)"
    assert data["language"] == "de-CH"


# ── Error paths ───────────────────────────────────────────────────────────────


@pytest.mark.anyio
async def test_migros_login_failure_returns_503(client):
    """Verify /login/migros returns 503 when the login flow fails (e.g. wrong credentials)."""
    with patch(
        "main.migros_stealth_login",
        new_callable=AsyncMock,
        side_effect=RuntimeError("Migros authentication failed — still on login page"),
    ):
        response = client.post("/login/migros", json={"email": "user@example.com", "password": "wrong"})

    assert response.status_code == 503
    assert "authentication failed" in response.json()["detail"].lower()


@pytest.mark.anyio
async def test_migros_login_unexpected_error_returns_500(client):
    """Verify /login/migros returns 500 on an unexpected internal error."""
    with patch(
        "main.migros_stealth_login",
        new_callable=AsyncMock,
        side_effect=Exception("DB connection reset"),
    ):
        response = client.post("/login/migros", json={"email": "user@example.com", "password": "pass"})

    assert response.status_code == 500
    assert "Internal error" in response.json()["detail"]
