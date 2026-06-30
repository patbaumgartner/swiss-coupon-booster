"""Stealth login sidecar for Coop Supercard and Migros Cumulus.

FastAPI application entry point. Delegates all login logic to the vendor-specific
modules (coop.py, migros.py) and exposes a uniform HTTP API.

Endpoints
---------
POST /login/coop
    Body: {"email": "...", "password": "..."}
    Returns 200: {"cookies": [...], "userAgent": "...", "language": "..."}
    Returns 503: when DataDome challenge or login flow fails
    Returns 500: on unexpected internal errors

POST /login/migros
    Body: {"email": "...", "password": "..."}
    Returns 200: {"cookies": [...], "userAgent": "...", "language": "..."}
    Returns 503: when login flow fails (wrong credentials or page not reachable)
    Returns 500: on unexpected internal errors

GET /health
    Returns 200: {"status": "ok"}
"""

from __future__ import annotations

import logging
import os
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, EmailStr

from coop import coop_stealth_login
from migros import migros_stealth_login

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.DEBUG if os.getenv("LOG_LEVEL", "info").lower() == "debug" else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
log = logging.getLogger("patchright")


# ── Models ────────────────────────────────────────────────────────────────────


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


# ── Application ───────────────────────────────────────────────────────────────


@asynccontextmanager
async def lifespan(app: FastAPI):  # noqa: ARG001
    log.info("Patchright sidecar starting")
    yield
    log.info("Patchright sidecar shutting down")


app = FastAPI(title="Patchright Login Sidecar", lifespan=lifespan)


# ── Endpoints ─────────────────────────────────────────────────────────────────


@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})


@app.post("/login/coop")
async def login_coop(request: LoginRequest) -> JSONResponse:
    log.info("Coop login request for: %s", request.email)
    start = time.time()
    try:
        result = await coop_stealth_login(request.email, request.password)
        elapsed_ms = int((time.time() - start) * 1000)
        log.info("Coop login successful in %d ms, %d cookies", elapsed_ms, len(result["cookies"]))
        return JSONResponse(result)
    except RuntimeError as exc:
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("Coop login failed in %d ms: %s", elapsed_ms, exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("Unexpected error in Coop login after %d ms: %s", elapsed_ms, exc, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal error: {exc}") from exc


@app.post("/login/migros")
async def login_migros(request: LoginRequest) -> JSONResponse:
    log.info("Migros login request for: %s", request.email)
    start = time.time()
    try:
        result = await migros_stealth_login(request.email, request.password)
        elapsed_ms = int((time.time() - start) * 1000)
        log.info("Migros login successful in %d ms, %d cookies", elapsed_ms, len(result["cookies"]))
        return JSONResponse(result)
    except RuntimeError as exc:
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("Migros login failed in %d ms: %s", elapsed_ms, exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("Unexpected error in Migros login after %d ms: %s", elapsed_ms, exc, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal error: {exc}") from exc
