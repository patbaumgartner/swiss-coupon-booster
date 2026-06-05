# Contributing to Swiss Coupon Booster

Thank you for taking the time to contribute! This guide will help you get up and
running quickly and explains the conventions used in this project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Getting started](#getting-started)
- [Development workflow](#development-workflow)
- [Commit messages](#commit-messages)
- [Pull request guidelines](#pull-request-guidelines)
- [Project conventions](#project-conventions)

---

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating you agree to abide by its terms. Please report unacceptable
behaviour to the maintainer.

---

## Ways to contribute

- **Bug reports** — found something broken? Open an issue using the
  [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) template.
- **Feature requests** — have an idea? Use the
  [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) template.
- **Code contributions** — fix a bug, implement a feature, improve tests or
  documentation. See the workflow below.
- **Documentation** — improve the README, add examples, fix typos.

---

## Getting started

### 1. Fork and clone

```sh
# Fork the repo on GitHub, then:
git clone https://github.com/YOUR_USERNAME/swiss-coupon-booster.git
cd swiss-coupon-booster
git remote add upstream https://github.com/patbaumgartner/swiss-coupon-booster.git
```

### 2. Set up the Python sidecar

```sh
cd stealth-service
uv sync --frozen --all-groups
uv run python -m patchright install chromium
```

### 3. Set up the Java application

```sh
cd coupon-booster
# First-time: install Playwright browsers
mvn exec:java -e \
  -Dexec.mainClass="com.microsoft.playwright.CLI" \
  -Dexec.args="install --with-deps"
```

### 4. Configure your environment

```sh
cp .env.example .env
# Edit .env with your Migros and Coop credentials
```

---

## Development workflow

```sh
# Create a feature branch from main
git checkout -b feature/my-feature   # or fix/my-fix, docs/my-docs

# Make your changes, then run the tests:

# Java
cd coupon-booster
mvn spring-javaformat:apply   # auto-format before committing
mvn verify                    # format check + tests + SpotBugs + JaCoCo

# Python
cd stealth-service
uv run pytest -v --tb=short

# Commit and push
git commit -m "feat: add support for ..."
git push origin feature/my-feature

# Open a Pull Request on GitHub
```

---

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/). A commit
message should look like:

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

| Type | When to use |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no logic change |
| `refactor` | Code change without feature or fix |
| `test` | Adding or improving tests |
| `chore` | Build process, dependency updates |
| `ci` | CI / GitHub Actions changes |

**Examples:**

```
feat(stealth): add proxy support to Migros login
fix(coop): retry on transient HTTP 503
docs: add scheduling guide to README
test(python): cover unexpected error branch in /login/migros
ci: add concurrency cancellation to CI workflow
```

---

## Pull request guidelines

1. **One PR per concern** — keep PRs small and focused.
2. **Tests required** — new features and bug fixes must include tests. Aim to
   keep coverage high.
3. **All CI checks must pass** — the PR will not be merged if tests fail,
   formatting is wrong, or Docker builds break.
4. **Update documentation** — if your change affects user-facing behaviour,
   update `README.md` and `.env.example`.
5. **Describe what and why** — fill in the PR template so reviewers have context.

---

## Project conventions

### Java (`coupon-booster/`)

| Convention | Tool |
|---|---|
| Code style | [Spring Java Format](https://github.com/spring-io/spring-javaformat) — run `mvn spring-javaformat:apply` |
| Static analysis | SpotBugs — run `mvn spotbugs:check` |
| Test coverage | JaCoCo — run `mvn verify`, report in `target/site/jacoco/` |
| Dependency management | All versions in `pom.xml` `<properties>` block |
| Configuration properties | `@ConfigurationProperties` records with `@ConfigurationPropertiesScan` |

### Python (`stealth-service/`)

| Convention | Tool |
|---|---|
| Package management | `uv` with `pyproject.toml` + `uv.lock` |
| Testing | pytest with strict asyncio mode (`pyproject.toml`) |
| Test files | `test_*.py` split by area (`test_coop.py`, `test_migros.py`, etc.) |

### Docker

- `docker-compose.yml` — **production only**, pulls from Docker Hub.
- `docker-compose.build.yml` — **development/CI**, builds from source.
- `docker-compose.sidecar.yml` — **local dev**, Spring Boot Docker Compose integration auto-starts stealth-service only.
- Never add credentials or secrets to Dockerfiles or Compose files.

### Branch naming

| Prefix | Purpose |
|---|---|
| `feature/` | New functionality |
| `fix/` | Bug fixes |
| `docs/` | Documentation only |
| `ci/` | CI / GitHub Actions |
| `deps/` | Dependency upgrades |

---

Thank you for contributing! ❤️
