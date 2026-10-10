#!/usr/bin/env bash
# Run the Hermes Remote bridge against a fake, synthetic Hermes backend.
#
# Nothing here touches a real Hermes install: the state directory, devices file, TLS identity,
# spawn ledger and port all live under demo/.state. Use it to try the Android app, to record the
# demo screenshots, or to work on the UI without burning model calls.
#
#   ./demo/run-demo.sh            # start mock Hermes + bridge, print the pairing URL
#   ./demo/run-demo.sh --pair     # also pair a throwaway device and print its token
#
# Then install the app, point it at the printed URL, and pair with the token.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE="$ROOT/demo/.state"
PORT="${DEMO_PORT:-8765}"
HERMES_PORT="${DEMO_HERMES_PORT:-8766}"

cd "$ROOT"

if [ ! -x bridge/.venv/bin/hermes-remote-bridge ]; then
  echo "bridge/.venv missing. Run: (cd bridge && uv sync)" >&2
  exit 1
fi

mkdir -p "$STATE"

# A demo config: its own port, its own device registry, LAN on so a phone on the same Wi-Fi
# can reach it, Tailscale off (the fake backend has no tailnet identity to check). hermes_home
# points at the demo's own spawn ledger, which the fake backend registers itself in, exactly as
# `hermes serve` does; start_hermes = false so the bridge never launches a real Hermes.
cat > "$STATE/config.toml" <<EOF
port = $PORT
listen = ["127.0.0.1"]
lan = true
mdns = false
hermes_home = "$STATE/hermes-home"
start_hermes = false
hermes_bin = "$ROOT/demo/fake-hermes-cli"
allowed_logins = []
devices_file = "$STATE/devices.json"
trust_file = "$STATE/networks.json"
tls_dir = "$STATE/tls"
audit_log = "$STATE/audit.log"
EOF

cleanup() {
  [ -n "${MOCK_PID:-}" ] && kill "$MOCK_PID" 2>/dev/null || true
  [ -n "${BRIDGE_PID:-}" ] && kill "$BRIDGE_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting the fake hermes serve on 127.0.0.1:$HERMES_PORT ..."
DEMO_HERMES_PORT="$HERMES_PORT" DEMO_HERMES_HOME="$STATE/hermes-home" \
  bridge/.venv/bin/python demo/mock_hermes.py &
MOCK_PID=$!
sleep 2

# The stub `hermes` CLI (demo/fake-hermes-cli) stores its two settings here, so changing the
# approval mode in the app never touches the real Hermes configuration.
printf '{"approvals.mode": "manual", "agent.reasoning_effort": "medium"}\n' > "$STATE/hermes-config.json"
export DEMO_HERMES_CONFIG="$STATE/hermes-config.json"

# Trust the current network so the bridge also serves it over pinned HTTPS (the phone needs
# that; loopback is not reachable from a real device). Non-interactive, into demo state only.
bridge/.venv/bin/hermes-remote-bridge --config "$STATE/config.toml" trust || true

echo "Starting the bridge on port $PORT ..."
# demo/serve.py is the real bridge CLI with the Tailscale socket and the host identity swapped
# for the fakes in this directory; the backend it finds is the fake one above.
bridge/.venv/bin/python demo/serve.py --config "$STATE/config.toml" serve &
BRIDGE_PID=$!
sleep 3

LAN_IP="$(ip -4 addr show scope global | grep -oP 'inet \K[0-9.]+' | head -1)"
export DEMO_LAN_IP="$LAN_IP"
cat <<EOF

Demo is up.

  bridge   https://$LAN_IP:$PORT   (pinned HTTPS, trusted local network)
           http://127.0.0.1:$PORT   (loopback)
  state    $STATE

EOF

if [ "${1:-}" = "--pair" ]; then
  # The demo device registry survives between runs; drop yesterday's device so --pair works
  # again without deleting the state directory by hand.
  bridge/.venv/bin/hermes-remote-bridge --config "$STATE/config.toml" revoke demo >/dev/null 2>&1 || true
  bridge/.venv/bin/hermes-remote-bridge --config "$STATE/config.toml" pair demo --url "https://$LAN_IP:$PORT"
  echo
  echo "Pair the app with that token, then send any message. Ctrl-C stops both processes."
fi

wait
