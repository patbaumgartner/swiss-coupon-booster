# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Patchright Stealth Login Sidecar
# Runs Patchright (hardened Chromium) + FastAPI to perform stealth login on
# supercard.ch and return session cookies to the Java coupon-booster app.
# ─────────────────────────────────────────────────────────────────────────────
FROM python:3.14-slim

# Install Chromium system deps required by Patchright
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Chromium core dependencies
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
    # Virtual display for headless with Xvfb fallback
    xvfb \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

ENV PATH="/app/.venv/bin:${PATH}"

# Install Patchright's patched Chromium browser
RUN uv run python -m patchright install chromium

COPY *.py entrypoint.sh ./
RUN chmod +x entrypoint.sh

# Persistent browser data and screenshot output will be mounted as volumes
RUN mkdir -p /data/coop-user-data /data/migros-user-data /data/screenshots

# Expose FastAPI port
EXPOSE 8000

ENV PYTHONUNBUFFERED=1
# HEADLESS is forced to false by entrypoint.sh — Xvfb provides the virtual display.
# headless-shell is detectable by DataDome; full Chromium + Xvfb is not.
ENV HEADLESS=false
ENV LOG_LEVEL=info
ENV COOP_USER_DATA_DIR=/data/coop-user-data
ENV MIGROS_USER_DATA_DIR=/data/migros-user-data
ENV SCREENSHOT_DIR=/data/screenshots
ENV DISPLAY=:99

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"

CMD ["/app/entrypoint.sh"]
