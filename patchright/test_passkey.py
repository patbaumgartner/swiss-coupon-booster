"""Tests for passkey credential persistence and Cloudflare URL detection."""

from __future__ import annotations

import importlib
import json
import stat
from pathlib import Path

import pytest

import config
import migros


@pytest.fixture
def passkey_file(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    target = tmp_path / "passkeys" / "migros-passkey.json"
    monkeypatch.setattr(config, "MIGROS_PASSKEY_FILE", target)
    monkeypatch.setattr(migros, "MIGROS_PASSKEY_FILE", target)
    return target


_VALID = {"credentialId": "abc", "privateKey": "key", "rpId": "migros.ch", "userHandle": "u", "signCount": 1}


def test_saved_credential_is_never_world_readable(passkey_file: Path) -> None:
    """The file holds a WebAuthn private key."""
    migros.save_passkey_credential(_VALID)

    mode = passkey_file.stat().st_mode
    assert not mode & stat.S_IRGRP, "group must not be able to read the private key"
    assert not mode & stat.S_IROTH, "others must not be able to read the private key"
    assert stat.S_IMODE(mode) == 0o600


def test_credential_is_created_restricted_without_relying_on_chmod(
    passkey_file: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Guards the TOCTOU window: writing 0644 and narrowing afterwards leaves the
    private key world-readable in between, and leaves it exposed for good wherever
    chmod is unavailable."""

    def refuse_chmod(*_args: object, **_kwargs: object) -> None:
        raise OSError("chmod unsupported on this filesystem")

    monkeypatch.setattr(migros.os, "chmod", refuse_chmod)
    migros.save_passkey_credential(_VALID)

    assert stat.S_IMODE(passkey_file.stat().st_mode) == 0o600


def test_save_then_load_round_trips(passkey_file: Path) -> None:
    migros.save_passkey_credential(_VALID)
    assert migros.load_passkey_credential() == _VALID


def test_load_returns_none_when_absent(passkey_file: Path) -> None:
    assert migros.load_passkey_credential() is None


def test_load_returns_none_for_malformed_json(passkey_file: Path) -> None:
    passkey_file.parent.mkdir(parents=True, exist_ok=True)
    passkey_file.write_text("{not json", encoding="utf-8")
    assert migros.load_passkey_credential() is None


@pytest.mark.parametrize("missing", ["credentialId", "privateKey", "rpId"])
def test_load_rejects_credentials_missing_required_fields(passkey_file: Path, missing: str) -> None:
    incomplete = {k: v for k, v in _VALID.items() if k != missing}
    passkey_file.parent.mkdir(parents=True, exist_ok=True)
    passkey_file.write_text(json.dumps(incomplete), encoding="utf-8")
    assert migros.load_passkey_credential() is None


@pytest.mark.parametrize(
    ("url", "expected"),
    [
        ("https://challenges.cloudflare.com/turnstile/v0/api.js", True),
        ("https://sub.challenges.cloudflare.com/x", True),
        ("https://challenges.cloudflare.com.attacker.test/x", False),
        ("https://notchallenges.cloudflare.com/x", False),
        ("https://login.migros.ch/", False),
        ("", False),
    ],
)
def test_cloudflare_challenge_url_matches_on_host_not_substring(url: str, expected: bool) -> None:
    assert migros._is_cloudflare_challenge_url(url) is expected


def test_launch_options_carry_the_automation_masking_flag() -> None:
    """Without this flag navigator.webdriver stays true over the CDP pipe."""
    import browser

    assert "--disable-blink-features=AutomationControlled" in browser.build_launch_options()["args"]


def test_launch_options_omit_proxy_when_unset(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("PROXY_URL", raising=False)
    importlib.reload(config)
    import browser

    importlib.reload(browser)
    try:
        assert "proxy" not in browser.build_launch_options()
        assert "proxy" not in browser.build_context_options()
    finally:
        monkeypatch.undo()
        importlib.reload(config)
        importlib.reload(browser)


def test_context_options_present_as_a_swiss_desktop_user() -> None:
    import browser

    options = browser.build_context_options()
    assert options["locale"] == "de-CH"
    assert options["timezone_id"] == "Europe/Zurich"
    assert options["is_mobile"] is False
