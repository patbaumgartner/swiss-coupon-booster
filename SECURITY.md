# Security Policy

## Supported Versions

Only the latest release is actively maintained and receives security updates.

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Older releases | ❌ |

## What this project handles

Both services handle live retailer credentials, so the security-relevant surface is
small but real:

| Asset | Where it lives | Handling |
|---|---|---|
| Retailer e-mail / password | `.env`, passed to the sidecar over the internal Docker network | Git-ignored; never logged |
| Migros passkey private key | `migros-passkey.json`, bind-mounted read-only | Git-ignored; written `0600` via `os.open`, never chmod-ed after the fact |
| Session cookies | In memory, and in the sidecar's browser profile volumes | Matched to a host per RFC 6265 so one retailer's cookies are never sent to the other |
| Proxy URL | `PROXY_URL` | Redacted to `scheme://***@host:port` before logging, since the documented format embeds credentials |

Login failures are logged in full but never returned to the HTTP caller: the
exception text can contain page content.

## Known hardening gaps

These are deliberate, documented trade-offs rather than oversights.

**The `patchright` sidecar runs as root.** Adding a `USER` directive is not a safe
drop-in change:

- Existing deployments have `/data/*` named volumes owned by root. Docker only
  applies image ownership when a volume is first created, so a non-root container
  could not write the browser profiles it already has.
- `migros-passkey.json` is bind-mounted from the host at `0600`. A container user
  whose UID does not match the host owner cannot read it, which would silently
  disable passkey login.
- Chromium's sandbox behaviour differs between root and non-root, and this project
  deliberately does not pass `--no-sandbox`.

Anyone hardening a deployment should pick a UID matching the host owner of
`migros-passkey.json`, recreate the `/data` volumes, and confirm Chromium still
launches before relying on it.

**The manual activation endpoint is unauthenticated.** It only exists in the
`server` profile and the container publishes no port by default, so it is
reachable from inside the Docker network only. Put authentication in front of it
before exposing it to a host or network.

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Report security issues by opening a
[GitHub private security advisory](https://github.com/patbaumgartner/swiss-coupon-booster/security/advisories/new).
Include:

- A clear description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested mitigations

You can expect an initial response within a few days. Once assessed, a fix will
be released promptly and the advisory will be published.
