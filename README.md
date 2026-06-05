<div align="center">

# 🛒 Swiss Coupon Booster

**Automatically activates all available digital coupons for Migros (Cumulus) and Coop (Supercard)**

[![CI](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-blue?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Python 3.14](https://img.shields.io/badge/Python-3.14-blue?logo=python)](https://www.python.org/downloads/release/python-3120/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Docker Hub – coupon-booster](https://img.shields.io/docker/v/patbaumgartner/coupon-booster?label=coupon-booster&logo=docker&color=blue)](https://hub.docker.com/r/patbaumgartner/coupon-booster)
[![Docker Hub – stealth-service](https://img.shields.io/docker/v/patbaumgartner/stealth-service?label=stealth-service&logo=docker&color=blue)](https://hub.docker.com/r/patbaumgartner/stealth-service)

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
│  │    stealth-service     │      │      coupon-booster       │  │
│  │  (Python 3.14/FastAPI) │◄─────│    (Java 25/Spring Boot)  │  │
│  │                        │      │                           │  │
│  │  POST /login/coop      │      │  1. Authenticate via      │  │
│  │  POST /login/migros    │      │     stealth-service       │  │
│  │                        │      │  2. Fetch available       │  │
│  │  Patchright + Xvfb     │      │     coupons via REST API  │  │
│  │  → bypasses DataDome   │      │  3. Activate all coupons  │  │
│  └────────────────────────┘      └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Why a sidecar?**  
Both Migros and Coop protect their login pages with [DataDome](https://datadome.co) bot
detection. A standard headless browser is instantly flagged. The **stealth-service** sidecar
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
│   ├── pom.xml
│   └── Dockerfile           # Build context: repo root (needs .git for git info)
├── stealth-service/         # Patchright login sidecar (Python 3.14 / FastAPI)
│   ├── main.py              # FastAPI app — POST /login/coop, POST /login/migros, GET /health
│   ├── test_*.py            # pytest test suite
│   ├── entrypoint.sh        # Starts Xvfb and uvicorn
│   ├── pyproject.toml       # uv dependency source + pytest config
│   ├── uv.lock              # Locked Python dependencies for reproducible installs
│   └── Dockerfile
├── docker-compose.yml       # Production — pulls images from Docker Hub
├── docker-compose.build.yml # Development — builds images from source
├── .env.example             # Configuration template — copy to .env
└── .github/
    ├── workflows/
    │   ├── ci.yml           # On push / PR: test Java + Python, validate Docker builds
    │   └── release.yml      # On tag: build, test, push to Docker Hub, publish release
    └── dependabot.yml       # Automated dependency updates (Maven, pip, Actions)
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

`stealth-service` starts first and waits until its health check passes (up to 40 s). Then
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
cd coupon-booster

# Profile enables Spring MVC mode + @Scheduled jobs
SPRING_PROFILES_ACTIVE=server mvn spring-boot:run
```

Default schedule (server profile):

- Coop daily at 06:00 Europe/Zurich
- Migros daily at 06:10 Europe/Zurich

Startup runners are disabled by default in server profile to avoid duplicate runs.
Enable one-time boot run explicitly with:

- COOP_STARTUP_RUN_ENABLED=true
- MIGROS_STARTUP_RUN_ENABLED=true

Override via `.env` or environment variables:

- `COOP_SCHEDULER_CRON`
- `MIGROS_SCHEDULER_CRON`
- `COUPONBOOSTER_SCHEDULER_ZONE`
- `COOP_SCHEDULER_ENABLED`
- `MIGROS_SCHEDULER_ENABLED`

### Option 2: External cron (Linux/NAS)

For containerized one-shot runs, keep using host cron.

```sh
# Run every Monday at 07:00
0 7 * * 1 cd /path/to/swiss-coupon-booster && docker compose pull -q && docker compose up --no-color >> /var/log/coupon-booster.log 2>&1
```

### Docker restart policy note

The one-shot app exits with code `0` when done. Using `restart: on-failure` will not cause it
to loop. For one-shot container mode, a cron job calling `docker compose up` is the
recommended approach.

---

## Local development

### stealth-service (Python)

```sh
cd stealth-service

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

# First-time: install Playwright Chromium (used by the browser auth fallback)
mvn exec:java -e \
  -Dexec.mainClass="com.microsoft.playwright.CLI" \
  -Dexec.args="install --with-deps"

# Run the application
# The ../.env file at the repo root is loaded automatically via spring.config.import
# When COOP_AUTH_MODE=sidecar and/or MIGROS_AUTH_MODE=sidecar, Spring Boot
# auto-starts docker-compose.sidecar.yml (stealth-service only).
mvn spring-boot:run

# Unit tests only
mvn test

# Full verify: formatting, unit + integration tests, SpotBugs, JaCoCo coverage
mvn verify
```

---

## Configuration reference

Copy `.env.example` to `.env` and fill in the required values.

### Required

| Variable | Description |
|---|---|
| `MIGROS_USER_EMAIL` | Migros / Cumulus account e-mail |
| `MIGROS_USER_PASSWORD` | Migros / Cumulus account password |
| `COOP_USER_EMAIL` | Coop / Supercard e-mail |
| `COOP_USER_PASSWORD` | Coop / Supercard password |

### Optional

| Variable | Default | Description |
|---|---|---|
| `COOP_AUTH_MODE` | `browser` | `browser` (local dev) or `sidecar` (Docker / production) |
| `MIGROS_AUTH_MODE` | `browser` | `browser` (local dev) or `sidecar` (Docker / production) |
| `MIGROS_LOGIN_ENABLED` | `true` | Enable or disable the Migros flow |
| `COOP_LOGIN_ENABLED` | `true` | Enable or disable the Coop flow |
| `MIGROS_STARTUP_RUN_ENABLED` | `true` | Run Migros once at application startup |
| `COOP_STARTUP_RUN_ENABLED` | `true` | Run Coop once at application startup |
| `VERSION` | `latest` | Docker Hub image tag to pull |
| `STEALTH_SLOW_MO_MS` | `500` | Milliseconds between browser actions |
| `STEALTH_TIMEOUT_MS` | `25000` | Browser element wait timeout (ms) |
| `STEALTH_LOG_LEVEL` | `info` | Sidecar log level (`debug` for verbose output) |
| `PROXY_URL` | _(none)_ | Optional HTTP or SOCKS5 residential proxy URL |
| `JAVA_OPTS` | `-XX:+UseZGC -Xmx512m` | JVM flags for coupon-booster |

### Auth modes

| Mode | When to use |
|---|---|
| `browser` **(local dev default)** | Login via Java Playwright directly. No sidecar required. Suitable for `mvn spring-boot:run` on a developer machine. May be challenged by DataDome on non-residential IPs. |
| `sidecar` **(Docker / production)** | Login delegated to `stealth-service`. Uses Patchright + Xvfb to bypass DataDome bot detection. Set via `COOP_AUTH_MODE=sidecar` / `MIGROS_AUTH_MODE=sidecar` — both Docker Compose files do this automatically. |

---

## Debug artifacts

When a login fails, screenshots and HTML page dumps are saved automatically.

```sh
# Copy screenshots out of the running (or stopped) container
docker compose cp stealth-service:/data/screenshots ./debug-screenshots
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
A: Yes. Set `MIGROS_LOGIN_ENABLED=false` or `COOP_LOGIN_ENABLED=false` in your `.env`.

**Q: The login fails with a screenshot showing a CAPTCHA.**  
A: This almost always means the IP has been flagged. Try running on a different network
(home broadband vs. VPS). If you must run in a cloud environment, configure a residential
proxy via the `PROXY_URL` variable.

---

## Docker Hub images

| Image | Description |
|---|---|
| [`patbaumgartner/stealth-service`](https://hub.docker.com/r/patbaumgartner/stealth-service) | Python 3.14 / Patchright sidecar |
| [`patbaumgartner/coupon-booster`](https://hub.docker.com/r/patbaumgartner/coupon-booster) | Java 25 / Spring Boot application |

Images are built and pushed to Docker Hub automatically on every GitHub release via the
[release workflow](.github/workflows/release.yml).

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

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
