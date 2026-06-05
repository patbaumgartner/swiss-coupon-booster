#!/bin/sh
set -e
# Entrypoint for the coop-stealth sidecar.
#
# Patchright's patched Chromium must run with a real rendering context to pass
# DataDome's browser-verification fingerprinting.  We launch a virtual X display
# via Xvfb and set HEADLESS=false so the full Chromium binary is used instead of
# the stripped chromium-headless-shell.

export DISPLAY=:99
export HEADLESS=false

# Remove stale Xvfb lock/socket files from previous container starts.
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 2>/dev/null || true

# Ensure no orphaned Xvfb process is still bound to DISPLAY=:99.
pkill -f "Xvfb :99" 2>/dev/null || true

# Remove Chrome singleton locks left by previous runs (e.g. container restart).
for _dir in /data/coop-user-data /data/migros-user-data; do
    find "${_dir}" -maxdepth 2 \
        \( -name 'SingletonLock' -o -name 'SingletonSocket' -o -name 'SingletonCookie' \) \
        -delete 2>/dev/null || true
done

# Start Xvfb virtual display in background
Xvfb "${DISPLAY}" -screen 0 1920x1080x24 -nolisten tcp &
XVFB_PID=$!
echo "Started Xvfb PID=${XVFB_PID} on ${DISPLAY}"

# Forward shutdown to Xvfb so lock files are not left behind.
cleanup() {
    if kill -0 "${XVFB_PID}" 2>/dev/null; then
        kill "${XVFB_PID}" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

# Give Xvfb time to initialise
sleep 2

if ! kill -0 "${XVFB_PID}" 2>/dev/null; then
    echo "ERROR: Xvfb failed to start" >&2
    exit 1
fi
echo "Xvfb ready"

# Replace this shell with uvicorn (becomes PID 1 / the container's main process)
exec uvicorn main:app --host 0.0.0.0 --port 8000
