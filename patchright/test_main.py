"""Tests for the shared login endpoint behaviour in main.py."""

from __future__ import annotations

import asyncio
from typing import Any
from unittest.mock import AsyncMock, patch

import httpx2
import pytest

import main

_RESULT: dict[str, Any] = {"cookies": [], "userAgent": "UA", "language": "de-CH"}
_CREDENTIALS = {"email": "user@example.com", "password": "secret"}


def _async_client() -> httpx2.AsyncClient:
    return httpx2.AsyncClient(transport=httpx2.ASGITransport(app=main.app), base_url="http://sidecar")


@pytest.mark.anyio
@pytest.mark.parametrize("provider", ["coop", "migros"])
async def test_concurrent_login_for_same_provider_is_rejected(provider: str) -> None:
    """A second login while one is in flight must not attach a second browser to
    the shared Chromium profile directory."""
    started = asyncio.Event()
    release = asyncio.Event()

    async def blocking_login(_email: str, _password: str) -> dict[str, Any]:
        started.set()
        await release.wait()
        return _RESULT

    with patch(f"main.{provider}_stealth_login", new=blocking_login):
        async with _async_client() as client:
            first = asyncio.create_task(client.post(f"/login/{provider}", json=_CREDENTIALS))
            await asyncio.wait_for(started.wait(), timeout=5)

            # Must be rejected immediately. Without the guard this call would
            # block behind the in-flight login instead of returning.
            second = await asyncio.wait_for(client.post(f"/login/{provider}", json=_CREDENTIALS), timeout=5)
            assert second.status_code == 409
            assert "already in progress" in second.json()["detail"]

            release.set()
            assert (await first).status_code == 200

    assert not main._LOGIN_LOCKS[provider].locked(), "lock must be released after the request completes"


@pytest.mark.anyio
async def test_providers_do_not_block_each_other() -> None:
    """Coop and Migros use separate profiles, so they may run concurrently."""
    release = asyncio.Event()

    async def blocking_login(_email: str, _password: str) -> dict[str, Any]:
        await release.wait()
        return _RESULT

    with (
        patch("main.coop_stealth_login", new=blocking_login),
        patch("main.migros_stealth_login", new_callable=AsyncMock, return_value=_RESULT),
    ):
        async with _async_client() as client:
            coop = asyncio.create_task(client.post("/login/coop", json=_CREDENTIALS))
            await asyncio.sleep(0)

            migros = await client.post("/login/migros", json=_CREDENTIALS)
            assert migros.status_code == 200

            release.set()
            assert (await coop).status_code == 200


@pytest.mark.anyio
async def test_lock_is_released_when_the_login_fails() -> None:
    with patch("main.coop_stealth_login", new_callable=AsyncMock, side_effect=RuntimeError("boom")):
        async with _async_client() as client:
            assert (await client.post("/login/coop", json=_CREDENTIALS)).status_code == 503
    assert not main._LOGIN_LOCKS["coop"].locked()


@pytest.mark.anyio
async def test_login_that_exceeds_the_timeout_returns_503() -> None:
    async def never_returns(_email: str, _password: str) -> dict[str, Any]:
        await asyncio.Event().wait()
        raise AssertionError("unreachable")

    with patch("main.coop_stealth_login", new=never_returns), patch.object(main, "LOGIN_TIMEOUT_S", 0.05):
        async with _async_client() as client:
            response = await client.post("/login/coop", json=_CREDENTIALS)

    assert response.status_code == 503
    assert "timed out" in response.json()["detail"]
    assert not main._LOGIN_LOCKS["coop"].locked()


@pytest.mark.anyio
async def test_unexpected_errors_do_not_leak_their_message() -> None:
    """The exception text can embed page content or credentials."""
    secret = "password=hunter2"
    with patch("main.coop_stealth_login", new_callable=AsyncMock, side_effect=ValueError(secret)):
        async with _async_client() as client:
            response = await client.post("/login/coop", json=_CREDENTIALS)

    assert response.status_code == 500
    assert response.json()["detail"] == "Internal error during login"
    assert secret not in response.text
