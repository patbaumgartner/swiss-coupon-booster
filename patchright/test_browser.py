"""Tests for shared browser helpers."""

from __future__ import annotations

import importlib

import pytest

import browser
import config


@pytest.mark.parametrize(
    ("proxy_url", "expected"),
    [
        (None, "<none>"),
        ("http://proxy.example.com:8080", "http://proxy.example.com:8080"),
        ("http://alice:hunter2@proxy.example.com:8080", "http://***@proxy.example.com:8080"),
        ("socks5://alice:hunter2@10.0.0.1:1080", "socks5://***@10.0.0.1:1080"),
        ("not a url", "<set>"),
    ],
)
def test_proxy_url_is_redacted_before_logging(
    monkeypatch: pytest.MonkeyPatch, proxy_url: str | None, expected: str
) -> None:
    monkeypatch.setenv("PROXY_URL", proxy_url) if proxy_url else monkeypatch.delenv("PROXY_URL", raising=False)
    reloaded = importlib.reload(config)
    try:
        assert reloaded.redacted_proxy_url() == expected
        if proxy_url and "hunter2" in proxy_url:
            assert "hunter2" not in reloaded.redacted_proxy_url()
    finally:
        monkeypatch.undo()
        importlib.reload(config)


def test_serialize_cookies_never_emits_null_strings() -> None:
    """The Java client binds domain and path as String, not Optional."""
    serialised = browser.serialize_cookies([{"name": "a", "value": "b"}])

    assert serialised == [
        {
            "name": "a",
            "value": "b",
            "domain": "",
            "path": "",
            "expires": -1,
            "httpOnly": False,
            "secure": False,
            "sameSite": "None",
        }
    ]


def test_serialize_cookies_preserves_supplied_attributes() -> None:
    serialised = browser.serialize_cookies(
        [{"name": "datadome", "value": "x", "domain": ".supercard.ch", "path": "/", "secure": True}]
    )

    assert serialised[0]["domain"] == ".supercard.ch"
    assert serialised[0]["secure"] is True


@pytest.mark.parametrize(
    ("url", "expected"),
    [
        ("https://geo.captcha-delivery.com/captcha/", True),
        ("https://captcha-delivery.com/x", True),
        ("https://evil-captcha-delivery.com/x", False),
        ("https://captcha-delivery.com.attacker.test/x", False),
        ("https://www.supercard.ch/", False),
        ("", False),
    ],
)
def test_datadome_url_detection_matches_on_host_not_substring(url: str, expected: bool) -> None:
    assert browser._is_datadome_url(url) is expected
