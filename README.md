<div align="center">

# 🛒 Swiss Coupon Booster

**Automatically activates all available digital coupons for Migros (Cumulus) and Coop (Supercard)**

[![CI](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/ci.yml)
[![Release](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/release.yml/badge.svg)](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/release.yml)
[![CodeQL](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/patbaumgartner/swiss-coupon-booster/security/code-scanning)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-yellow.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-blue?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Python 3.14](https://img.shields.io/badge/Python-3.14-blue?logo=python)](https://www.python.org/downloads/release/python-3140/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Docker Hub – coupon-booster](https://img.shields.io/docker/v/patbaumgartner/coupon-booster?label=coupon-booster&logo=docker&color=blue)](https://hub.docker.com/r/patbaumgartner/coupon-booster)
[![Docker Hub – patchright](https://img.shields.io/docker/v/patbaumgartner/coupon-booster-patchright?label=patchright&logo=docker&color=blue)](https://hub.docker.com/r/patbaumgartner/coupon-booster-patchright)

</div>

---

## Table of Contents

- [Why this project?](#why-this-project)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick start — Docker Hub](#quick-start--docker-hub-recommended)
- [Building from source](#building-from-source)
- [Scheduling](#scheduling)
- [Local development](#local-development)
- [Configuration reference](#configuration-reference)
- [Debug artifacts](#debug-artifacts)
- [FAQ](#faq)
- [Docker Hub images](#docker-hub-images)
- [Upgrading](#upgrading)
- [Contributing](#contributing)
- [License](#license)

---

## Why this project?

Both Migros and Coop publish dozens of digital discount coupons every week. Activating them
manually one by one across their apps or websites is tedious — and easy to forget. This project
automates the entire process: it logs in, discovers every available coupon, and activates them
all in a single run. Set it up once on your home server or NAS and let it run on a schedule.

---

## How it works

```
┌─────────────────────────────────────────────────────────────────┐
│                      docker compose up                          │
│                                                                 │
│  ┌────────────────────────┐      ┌───────────────────────────┐  │
│  │  patchright            │      │    coupon-booster         │  │
│  │  (Python 3.14/FastAPI) │◄─────│    (Java 25/Spring Boot)  │  │
│  │                        │      │                           │  │
│  │  POST /login/coop      │      │  1. Authenticate via      │  │
│  │  POST /login/migros    │      │     patchright            │  │
│  │                        │      │  2. Fetch available       │  │
│  │  Patchright + Xvfb     │      │     coupons via REST API  │  │
│  │  → bypasses DataDome   │      │  3. Activate all coupons  │  │
│  └────────────────────────┘      └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Why a sidecar?**  
Coops protect their login pages with [DataDome](https://datadome.co) bot
detection. A standard headless browser is instantly flagged. The **patchright** sidecar
uses [Patchright](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright) — a patched build of
Chromium that removes all automation fingerprints at the C++ level — running inside a virtual
Xvfb display. From DataDome's perspective this is indistinguishable from a real user.

> **Home network beats everything.** Swiss residential IPs are treated very differently from
> cloud or datacenter addresses. Running this on your home server or NAS means you will
> virtually never see a DataDome challenge — no paid proxy needed.

---

## Project structure

```
swiss-coupon-booster/
├── coupon-booster/          # Spring Boot application (Java 25 / Maven)
│   ├── src/
│   │   ├── main/java/       # Application sources
│   │   └── test/java/       # Unit + integration tests
│   └── pom.xml              # Image built by the Spring Boot Maven plugin (buildpacks)
├── patchright/              # Patchright login sidecar (Python 3.14 / FastAPI)
│   ├── main.py              # FastAPI app — POST /login/coop, POST /login/migros, GET /health
│   ├── test_*.py            # pytest test suite
│   ├── entrypoint.sh        # Starts Xvfb and uvicorn
│   ├── pyproject.toml       # uv dependency source + pytest config
│   ├── uv.lock              # Locked Python dependencies for reproducible installs
│   └── Dockerfile
├── docker-compose.yml        # Production — pulls images from Docker Hub
├── docker-compose.build.yml  # Development — builds images from source
├── docker-compose.sidecar.yml # Local dev — Spring Boot auto-starts patchright only
├── .env.example              # Configuration template — copy to .env
├── passkeys/                 # Optional, git-ignored — drop migros-passkey.json here
└── .github/
    ├── workflows/
    │   ├── ci.yml           # On push / PR: test Java + Python; on main: publish snapshot images
    │   └── release.yml      # On tag: build, push to Docker Hub, publish release
    └── dependabot.yml       # Automated dependency updates (Maven, uv, Actions)
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker | 24+ | With Compose v2 (comes bundled with Docker Desktop) |
| Git | any | For cloning the repository |
| Java 25 | JDK 25 | Only for local development without Docker |
| Python 3.14 | 3.14+ | Only for local development without Docker |

---

## Quick start — Docker Hub (recommended)

```sh
# 1. Clone the repository
git clone https://github.com/patbaumgartner/swiss-coupon-booster.git
cd swiss-coupon-booster

# 2. Create and fill in your configuration
cp .env.example .env
$EDITOR .env   # set MIGROS_USER_EMAIL, MIGROS_USER_PASSWORD, COOP_USER_EMAIL, COOP_USER_PASSWORD

# 3. Pull the pre-built images and run
docker compose pull
docker compose up
```

`patchright` starts first and waits until its health check passes (up to 40 s). Then
`coupon-booster` authenticates, activates all available coupons, and exits cleanly.

**Pinning a specific release:**

```sh
VERSION=1.2.0 docker compose up
```

---

## Building from source

Use `docker-compose.build.yml` when you want to run from local sources (e.g. after making
changes):

```sh
# Build both images and start
docker compose -f docker-compose.build.yml up --build

# Build without starting
docker compose -f docker-compose.build.yml build
```

---

## Scheduling

You have two options for automatic activation.

### Option 1: Built-in Spring scheduler (long-running server)

Run coupon-booster as a long-running Spring app with profile `server`.

```sh
# Docker (the usual case): set it in .env, then start the stack
echo 'SPRING_PROFILES_ACTIVE=server' >> .env
docker compose up -d
```

```sh
# Or locally, without Docker
cd coupon-booster

# Profile enables Spring MVC mode + @Scheduled jobs
SPRING_PROFILES_ACTIVE=server ./mvnw spring-boot:run
```

Under Docker you will also want `restart: unless-stopped` for `coupon-booster`
instead of the default `restart: "no"`, which is tuned for the one-shot run.

Default schedule (server profile):

- Coop daily at 06:00 Europe/Zurich
- Migros daily at 06:10 Europe/Zurich

Startup runners are disabled by default in server profile to avoid duplicate runs.
Enable one-time boot run explicitly with:

- `COOP_STARTUP_RUN_ENABLED=true`
- `MIGROS_STARTUP_RUN_ENABLED=true`

Override via `.env` or environment variables — see the [Scheduler](#scheduler-server-profile-only) section in the configuration reference.

#### Manual activation trigger (REST)

In `server` profile the app also exposes REST endpoints to start a run on demand,
using the same flow as the scheduler:

| Method & path           | Action                          |
| ----------------------- | ------------------------------- |
| `POST /activations/coop`   | Run Coop coupon activation   |
| `POST /activations/migros` | Run Migros coupon activation |

The call blocks until the run finishes and returns the outcome as JSON:

```json
{ "provider": "Coop", "authenticated": true, "activated": 5, "failed": 0, "authDurationMs": 4210, "message": "Activation completed" }
```

Responses:

- `200 OK` — run completed (see the JSON body for activated/failed counts).
- `409 Conflict` — a run (scheduled or manual) is already in progress for that provider.
- `503 Service Unavailable` — that provider's scheduler is disabled
  (`COOP_SCHEDULER_ENABLED=false` / `MIGROS_SCHEDULER_ENABLED=false`).

> [!WARNING]
> The endpoint has no authentication and triggers a credential-backed login. The
> `coupon-booster` container publishes no port by default, so it is reachable only
> from inside the Docker network. Trigger it from there:
>
> ```sh
> docker compose exec coupon-booster \
>   curl -fsS -X POST http://localhost:8080/activations/coop
> ```
>
> Add a port mapping (ideally bound to `127.0.0.1`) and put authentication in front
> before exposing it to a host or network.

### Option 2: External cron (Linux/NAS)

For containerized one-shot runs, keep using host cron.

```sh
# Run every Monday at 07:00
0 7 * * 1 cd /path/to/swiss-coupon-booster && docker compose pull -q && docker compose up --no-color >> /var/log/coupon-booster.log 2>&1
```

### Exit codes

The one-shot run reports its outcome so cron and `restart: on-failure` can act on it:

| Code | Meaning |
|---|---|
| `0` | Every enabled provider authenticated. Individual coupons may still have failed to activate — coupons expire and are withdrawn between runs — see the run summary in the log. |
| `1` | At least one provider could not authenticate: wrong credentials, sidecar unreachable, or a bot challenge. |

For one-shot container mode, a cron job calling `docker compose up` remains the
recommended approach.

---

## Local development

### patchright (Python)

```sh
cd patchright

# Install uv (if not already installed)
# https://docs.astral.sh/uv/getting-started/installation/

# Create/update .venv from lockfile (production + dev/test groups)
uv sync --frozen --all-groups

# Install the Patchright Chromium build
uv run python -m patchright install chromium

# Run the sidecar (a real display is enough on a desktop machine)
COOP_USER_DATA_DIR=./coop-user-data \
MIGROS_USER_DATA_DIR=./migros-user-data \
  uv run uvicorn main:app --reload --port 8000

# Run the test suite
uv run pytest -v --tb=short
```

### coupon-booster (Java)

```sh
cd coupon-booster

# Run the application
# The ../.env file at the repo root is loaded automatically via spring.config.import
# Spring Boot auto-starts docker-compose.sidecar.yml (patchright only), so the
# sidecar is available for login.
./mvnw spring-boot:run

# Unit tests only
./mvnw test

# Full verify: formatting, unit + integration tests, SpotBugs, JaCoCo coverage
./mvnw verify
```

---

## Configuration reference

Copy `.env.example` to `.env` and fill in the required values.

### Credentials

Supply the credentials for each retailer you want to use. A retailer you leave
unconfigured is reported as a failed login for that provider; the other still runs.

| Variable | Description |
|---|---|
| `MIGROS_USER_EMAIL` | Migros / Cumulus account e-mail |
| `MIGROS_USER_PASSWORD` | Migros / Cumulus account password |
| `COOP_USER_EMAIL` | Coop / Supercard e-mail |
| `COOP_USER_PASSWORD` | Coop / Supercard password |

### Sidecar endpoint

Login is always delegated to the `patchright` sidecar — see
[Why a sidecar?](#how-it-works). Both Docker Compose files wire this up, and
`mvn spring-boot:run` auto-starts `docker-compose.sidecar.yml`.

| Variable | Default | Description |
|---|---|---|
| `COOP_PATCHRIGHT_URL` | `http://localhost:8000` | Sidecar base URL used for Coop login |
| `MIGROS_PATCHRIGHT_URL` | `http://localhost:8000` | Sidecar base URL used for Migros login |
| `COUPONBOOSTER_SIDECAR_CONNECT_TIMEOUT` | `10s` | Connect timeout for sidecar calls |
| `COUPONBOOSTER_SIDECAR_READ_TIMEOUT` | `300s` | Read timeout — a cold login with a DataDome challenge is slow |

### Feature toggles

| Variable | Default | Description |
|---|---|---|
| `COOP_STARTUP_RUN_ENABLED` | `true` | Run Coop once at application startup |
| `MIGROS_STARTUP_RUN_ENABLED` | `true` | Run Migros once at application startup |

Credentials are optional: leave one retailer's variables unset and disable its
runner to use the other on its own.

### Scheduler (server profile only)

Activate with `SPRING_PROFILES_ACTIVE=server` to run as a long-lived Spring app with built-in daily jobs.

| Variable | Default | Description |
|---|---|---|
| `COOP_SCHEDULER_ENABLED` | `true` | Enable/disable the Coop scheduler |
| `MIGROS_SCHEDULER_ENABLED` | `true` | Enable/disable the Migros scheduler |
| `COOP_SCHEDULER_CRON` | `0 0 6 * * *` | Cron expression for Coop (daily 06:00) |
| `MIGROS_SCHEDULER_CRON` | `0 10 6 * * *` | Cron expression for Migros (daily 06:10) |
| `COUPONBOOSTER_SCHEDULER_ZONE` | `Europe/Zurich` | Timezone for all cron schedules |

### Docker image

| Variable | Default | Description |
|---|---|---|
| `VERSION` | `latest` | Docker Hub image tag for both services |

### Stealth sidecar tuning

| Variable | Default | Description |
|---|---|---|
| `PATCHRIGHT_SLOW_MO_MS` | `500` | Milliseconds between browser actions |
| `PATCHRIGHT_TIMEOUT_MS` | `25000` | Browser element wait timeout (ms) |
| `PATCHRIGHT_TYPING_DELAY_MS` | `80` | Delay between individual keystrokes (ms) |
| `PATCHRIGHT_LOG_LEVEL` | `info` | Sidecar log level (`debug` for verbose output) |
| `COOP_LOGIN_URL` | _(Supercard SSO URL)_ | Override the Coop login URL |
| `PROXY_URL` | _(none)_ | Optional HTTP or SOCKS5 residential proxy URL |
| `MIGROS_PASSKEY_DIR` | `./passkeys` | Host directory mounted read-only at `/data/passkeys` |

### Migros passkey login (optional)

The Migros account can be passkey-primary. If you register a passkey, the sidecar
signs the WebAuthn challenge itself — no password and no SMS 2FA prompt.

```sh
cd patchright
HEADLESS=false MIGROS_PASSKEY_FILE=../passkeys/migros-passkey.json \
  uv run python register_migros_passkey.py
```

The resulting `passkeys/migros-passkey.json` contains a **private key**. It is
git-ignored, written `0600`, and mounted read-only into the sidecar. Without it
the sidecar simply falls back to password login, so the directory may stay empty.

### Coop coupon selection

| Variable / property | Default | Description |
|---|---|---|
| `supercard.coupon-filter.max-active-coupons` | `20` | Upper bound per run — Supercard caps how many coupons may be active at once |
| `supercard.coupon-filter.include-shop` | `retail` | Only coupons redeemable in this channel are activated |
| `supercard.coupon-filter.always-include-discount-marker` | `5 Rappen` | Coupons whose discount text contains this marker bypass the product-type filter; blank disables it |
| `supercard.coupon-filter.include-product-types` | _(see `application.yml`)_ | A coupon qualifies only when all of its product types are listed |

---

## Debug artifacts

When a login fails, screenshots and HTML page dumps are saved automatically.

```sh
# Copy screenshots out of the running (or stopped) container
docker compose cp patchright:/data/screenshots ./debug-screenshots
ls -la ./debug-screenshots/
```

Screenshots are also preserved in the `screenshots` Docker volume between runs.

---

## FAQ

**Q: Will this get my account banned?**  
A: The application activates coupons exactly as a human user would through the normal web
interface. Both retailers explicitly publish these coupons for customers to use. There is no
report of any account being penalised for activating their own coupons.

**Q: Do I need a Swiss IP address?**  
A: You need a residential IP — ideally a Swiss one. DataDome assigns risk scores based on
IP reputation. Any residential broadband or home server/NAS works well. Cloud VPS addresses
(AWS, GCP, Hetzner, etc.) will frequently trigger challenges even with Patchright.

**Q: Why does the container need `--shm-size` or similar?**  
A: The Dockerfiles already configure the correct shared memory settings for Chromium. No
extra flags are needed with the provided `docker-compose.yml`.

**Q: Can I disable one retailer?**  
A: Yes. Set `COOP_STARTUP_RUN_ENABLED=false` or `MIGROS_STARTUP_RUN_ENABLED=false` in your `.env`.

**Q: The login fails with a screenshot showing a CAPTCHA.**  
A: This almost always means the IP has been flagged. Try running on a different network
(home broadband vs. VPS). If you must run in a cloud environment, configure a residential
proxy via the `PROXY_URL` variable.

---

## Docker Hub images

| Image | Description |
|---|---|
| [`patbaumgartner/coupon-booster-patchright`](https://hub.docker.com/r/patbaumgartner/coupon-booster-patchright) | Python 3.14 / Patchright sidecar |
| [`patbaumgartner/coupon-booster`](https://hub.docker.com/r/patbaumgartner/coupon-booster) | Java 25 / Spring Boot application (GraalVM native image) |

Both images are built and pushed on every GitHub release via the
[release workflow](.github/workflows/release.yml). `:latest` always points at the most
recent final release — pushes to `main` publish only a version-tagged snapshot, so
`docker compose pull` never moves you onto an unreleased build.

---

## Upgrading

### 0.2.0 — sidecar is the only login path

`COOP_AUTH_MODE` and `MIGROS_AUTH_MODE` were removed along with the in-process
Java Playwright login (`browser` mode). Login is always delegated to the
`patchright` sidecar, which is what production already used and the only mode
that reliably clears DataDome.

If you ran `AUTH_MODE=browser`:

- **Docker users** — nothing to do beyond deleting the variables from `.env`.
  Unknown variables are ignored, so an outdated `.env` will not break startup.
- **Local `mvn spring-boot:run`** — also nothing to do: Spring Boot auto-starts
  `docker-compose.sidecar.yml`.
- **Local without Docker** — run the sidecar yourself and tell Spring Boot not to
  manage Compose:

  ```sh
  cd patchright && uv run uvicorn main:app --port 8000 &
  cd coupon-booster && SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw spring-boot:run
  ```

These variables were removed and are now ignored: `COOP_AUTH_MODE`,
`MIGROS_AUTH_MODE`, `PLAYWRIGHT_HEADLESS`, `COOP_PLAYWRIGHT_USER_DATA_DIR`.

The `patbaumgartner/coupon-booster-run` image is no longer built or published: it
existed only to ship Chromium libraries for `browser` mode. The application image
now uses the buildpack's default run image.

---

## Contributing

Contributions are very welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before
submitting a pull request.

Quick checklist:

- All tests pass: `mvn verify` (Java) and `uv run pytest -v --tb=short` (Python)
- Code is formatted: `mvn spring-javaformat:apply` (Java)
- New environment variables are documented in `.env.example` and `README.md`

---

## License

Distributed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
