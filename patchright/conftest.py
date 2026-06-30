"""Shared pytest fixtures for the patchright test suite."""

import pytest
from fastapi.testclient import TestClient

from main import app


@pytest.fixture(scope="session")
def client() -> TestClient:
    """A reusable synchronous TestClient for the FastAPI application."""
    return TestClient(app)
