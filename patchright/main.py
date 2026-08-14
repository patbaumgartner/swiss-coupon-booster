"""Stealth login sidecar for Coop Supercard and Migros Cumulus.

FastAPI application entry point. Delegates all login logic to the vendor-specific
modules (coop.py, migros.py) and exposes a uniform HTTP API.

Endpoints
---------
POST /login/coop
    Body: {"email": "...", "password": "..."}
    Returns 200: {"cookies": [...], "userAgent": "...", "language": "..."}
    Returns 409: when a login for that provider is already in flight
    Returns 503: when the login flow fails or times out
    Returns 500: on unexpected internal errors

POST /login/migros
    Same contract as /login/coop.

GET /health
    Returns 200: {"status": "ok"}
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, EmailStr

from config import LOG_LEVEL, LOGIN_TIMEOUT_S
from coop import coop_stealth_login
from migros import migros_stealth_login

logging.basicConfig(
    level=logging.DEBUG if LOG_LEVEL.lower() == "debug" else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
log = logging.getLogger("patchright")


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


# Each provider drives a persistent Chromium profile pinned to one user-data
# directory. Two concurrent logins for the same provider would attach two
# browsers to that directory and corrupt the profile, so a provider serves one
# login at a time and rejects overlapping requests outright. Queueing instead
# would just push the caller past its own read timeout.
_LOGIN_LOCKS: dict[str, asyncio.Lock] = {"coop": asyncio.Lock(), "migros": asyncio.Lock()}


@asynccontextmanager
async def _single_flight(provider: str) -> AsyncIterator[None]:
    lock = _LOGIN_LOCKS[provider]
    if lock.locked():
        log.warning("%s login already in progress; rejecting concurrent request", provider)
        raise HTTPException(status_code=409, detail=f"A {provider} login is already in progress")
    async with lock:
        yield


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    log.info("Patchright sidecar starting")
    yield
    log.info("Patchright sidecar shutting down")


app = FastAPI(title="Patchright Login Sidecar", lifespan=lifespan)


@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})


def _elapsed(start: float) -> str:
    return f"{int((time.monotonic() - start) * 1000)} ms"


async def _login(
    provider: str,
    flow: Callable[[str, str], Awaitable[dict[str, Any]]],
    request: LoginRequest,
) -> JSONResponse:
    log.info("%s login request for: %s", provider, request.email)
    start = time.monotonic()
    async with _single_flight(provider):
        try:
            result = await asyncio.wait_for(flow(request.email, request.password), timeout=LOGIN_TIMEOUT_S)
        except TimeoutError as exc:
            log.error("%s login timed out after %ss", provider, LOGIN_TIMEOUT_S)
            raise HTTPException(status_code=503, detail=f"{provider} login timed out after {LOGIN_TIMEOUT_S}s") from exc
        except RuntimeError as exc:
            log.error("%s login failed in %s: %s", provider, _elapsed(start), exc)
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except Exception as exc:
            # The message can embed page content or credentials, so it is logged
            # but never returned to the caller.
            log.error("Unexpected error in %s login after %s", provider, _elapsed(start), exc_info=True)
            raise HTTPException(status_code=500, detail="Internal error during login") from exc

    log.info("%s login successful in %s, %d cookies", provider, _elapsed(start), len(result["cookies"]))
    return JSONResponse(result)


@app.post("/login/coop")
async def login_coop(request: LoginRequest) -> JSONResponse:
    return await _login("coop", coop_stealth_login, request)


@app.post("/login/migros")
async def login_migros(request: LoginRequest) -> JSONResponse:
    return await _login("migros", migros_stealth_login, request)
