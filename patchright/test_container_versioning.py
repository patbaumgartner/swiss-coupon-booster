"""Guard the single-source-of-truth rule for the browser version.

The Patchright pin in ``pyproject.toml`` (mirrored in ``uv.lock``) is the only
place where a browser version is declared. A versioned Playwright base image
would add a second pin that Dependabot does not update, so CI rejects it.
"""

from pathlib import Path

DOCKERFILE = Path(__file__).with_name("Dockerfile")


def test_dockerfile_does_not_pin_a_playwright_browser_version():
    dockerfile = DOCKERFILE.read_text(encoding="utf-8")
    from_lines = [line for line in dockerfile.splitlines() if line.startswith("FROM ")]

    assert from_lines, "Dockerfile must declare a base image"

    assert all("mcr.microsoft.com/playwright/" not in line for line in from_lines), (
        "Do not use a versioned Playwright base image: patchright/pyproject.toml is the single "
        "browser version source. Use a plain Python base and install system deps explicitly."
    )
