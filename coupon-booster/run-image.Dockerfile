# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Swiss Coupon Booster – custom Paketo run image
#
# Extends paketobuildpacks/run-jammy-base with:
#   • Chromium runtime libraries (for Java Playwright "browser" auth mode)
#   • xvfb (virtual framebuffer for headless Chromium)
#   • Pre-installed Playwright 1.60.0 Chromium browser at /ms-playwright
#   • /data/{screenshots,coop-user-data} volume mount points owned by cnb
#
# This image must be built before running 'mvn spring-boot:build-image'.
# The builder (paketobuildpacks/builder-jammy-base) and this run image share
# the Paketo jammy stack as required by the CNB specification.
#
# Usage (from repository root):
#   docker build -f coupon-booster/run-image.Dockerfile \
#                -t patbaumgartner/coupon-booster-run:latest .
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Download Playwright browser binaries ────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS browser-installer

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /installer

# Inline pom.xml that brings in playwright and all transitive dependencies
COPY <<'EOF' pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>installer</groupId>
  <artifactId>playwright-installer</artifactId>
  <version>1.0</version>
  <dependencies>
    <dependency>
      <groupId>com.microsoft.playwright</groupId>
      <artifactId>playwright</artifactId>
      <version>1.60.0</version>
    </dependency>
  </dependencies>
</project>
EOF

# Resolve all dependencies into deps/ then download the Chromium binary.
# --with-deps is omitted here; system libraries are installed in stage 2.
RUN mvn -q dependency:copy-dependencies -DoutputDirectory=deps \
    && java -cp "deps/*" com.microsoft.playwright.CLI install chromium

# ── Stage 2: Custom Paketo run image ─────────────────────────────────────────
FROM paketobuildpacks/run-noble-base

USER root

# Chromium runtime dependencies required by Java Playwright (browser-mode fallback)
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libnss3 \
    libnspr4 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libdbus-1-3 \
    libxcb1 \
    libxkbcommon0 \
    libx11-6 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libpango-1.0-0 \
    libcairo2 \
    libasound2t64 \
    libatspi2.0-0 \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

# Copy pre-downloaded Playwright browsers and make them world-readable for cnb
COPY --from=browser-installer /ms-playwright /ms-playwright
RUN chmod -R a+rX /ms-playwright

# Pre-create data volume mount points owned by cnb; Docker named volumes
# inherit these permissions on first mount so the app can read and write them
RUN mkdir -p /data/screenshots /data/coop-user-data \
    && chown -R cnb:cnb /data

# Tell Playwright where to find the pre-downloaded browsers at runtime
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

USER cnb
