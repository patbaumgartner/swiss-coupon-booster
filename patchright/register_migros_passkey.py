"""Interactive one-time helper to register a Migros passkey.

Launches a *headed* Chromium window with a CDP virtual authenticator attached
(WebAuthn enabled, no disable script) and opens the Migros login page. You then:

  1. Log in normally (e-mail + password, then the SMS/authenticator 2nd factor).
  2. Open your Migros account security / login-methods page and choose
     "Add passkey" (Passkey hinzufügen). Complete the prompt — the virtual
     authenticator answers it automatically.

As soon as a passkey for a ``*.migros.ch`` relying party appears in the virtual
authenticator, this script exports it (credential id + private key + signCount)
and writes it to ``MIGROS_PASSKEY_FILE``. After that the normal
``/login/migros`` endpoint will authenticate with the passkey — no password or
SMS needed.

Run it headed (WSLg / a display is required)::

    HEADLESS=false MIGROS_PASSKEY_FILE=./migros-passkey.json \\
        uv run python register_migros_passkey.py

The saved file contains a PRIVATE KEY — keep it secret and do not commit it.
"""

from __future__ import annotations

import asyncio
import time

from patchright.async_api import async_playwright

from browser import (
    create_persistent_context,
    enable_virtual_authenticator,
    get_virtual_credentials,
)
from config import MIGROS_LOGIN_URL, MIGROS_PASSKEY_FILE, MIGROS_USER_DATA_DIR
from migros import save_passkey_credential

POLL_INTERVAL_S = 2.0
TIMEOUT_S = 8 * 60  # 8 minutes to complete an interactive login + registration


async def main() -> int:
    MIGROS_USER_DATA_DIR.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as pw:
        context = await create_persistent_context(pw, MIGROS_USER_DATA_DIR)
        page = await context.new_page()
        cdp = await context.new_cdp_session(page)
        authenticator_id = await enable_virtual_authenticator(cdp)

        # Remember credentials already present so we only report the *new* one.
        existing = {c.get("credentialId") for c in await get_virtual_credentials(cdp, authenticator_id)}

        await page.goto(MIGROS_LOGIN_URL)

        print("=" * 72)
        print("Migros passkey registration")
        print("=" * 72)
        print("A browser window is open. In that window:")
        print("  1. Log in with your e-mail + password, then the SMS/2FA code.")
        print("  2. Go to your Migros account security / login methods page.")
        print("  3. Choose 'Add passkey' (Passkey hinzufuegen) and confirm the prompt.")
        print()
        print(f"Waiting up to {TIMEOUT_S // 60} minutes for a Migros passkey to appear...")
        print("(You can leave this running while you complete the steps above.)")
        print("=" * 72)

        deadline = time.monotonic() + TIMEOUT_S
        new_credential = None
        while time.monotonic() < deadline:
            creds = await get_virtual_credentials(cdp, authenticator_id)
            for cred in creds:
                rp_id = (cred.get("rpId") or "").lower()
                if "migros" in rp_id and cred.get("credentialId") not in existing:
                    new_credential = cred
                    break
            if new_credential:
                break
            await asyncio.sleep(POLL_INTERVAL_S)

        if new_credential is None:
            print("No Migros passkey was detected within the timeout. Nothing saved.")
            await context.close()
            return 1

        save_passkey_credential(new_credential)
        print()
        print(f"Success! Passkey for rpId='{new_credential.get('rpId')}' saved to:")
        print(f"  {MIGROS_PASSKEY_FILE}")
        print("You can close the browser window. Passkey login is now enabled.")

        await context.close()
        return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
