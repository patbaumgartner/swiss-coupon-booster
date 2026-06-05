# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Swiss Coupon Booster – Java Spring Boot application
# Multi-stage build: Maven → slim JRE runtime
#
# Build context must be the repository root so that .git is available for
# git-commit-id-maven-plugin to generate git.properties.
#
# Usage via docker-compose.build.yml (recommended):
#   docker compose -f docker-compose.build.yml build coupon-booster
#
# Direct build (from repo root):
#   docker build -f coupon-booster/Dockerfile -t patbaumgartner/coupon-booster .
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# Cache Maven dependency resolution separately from the source copy
# so that source changes don't invalidate the dependency layer.
COPY coupon-booster/pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and .git (required by git-commit-id-maven-plugin)
COPY coupon-booster/src ./src
COPY .git .git
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS runtime

# Install Chromium runtime dependencies required by Java Playwright (Migros browser-mode fallback)
RUN apt-get update && apt-get install -y --no-install-recommends \
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

WORKDIR /app

COPY --from=build /workspace/target/coupon-booster-*.jar app.jar

# Pre-download Playwright browsers used by the Migros browser-mode fallback
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium 2>/dev/null || true

# Persistent data directories (mounted as Docker volumes at runtime)
RUN mkdir -p /data/screenshots /data/coop-user-data

ENV JAVA_OPTS="-XX:+UseZGC -Xmx512m"
ENV HEADLESS=true

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
