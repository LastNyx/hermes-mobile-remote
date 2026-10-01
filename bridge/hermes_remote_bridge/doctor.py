"""`hermes-remote-bridge doctor`: explain why the phone can't connect, one check at a time."""
from __future__ import annotations

import shutil
import subprocess
import urllib.request

from . import firewall
from .config import Config, read_hermes_api_key
from .devices import DeviceStore
from .network import TrustStore, current_network, serving_lan_ips
from .tailnet import sync_tailscale_ips

OK, WARN, FAIL = "ok", "warn", "fail"
_MARK = {OK: "\u2714", WARN: "!", FAIL: "\u2718"}


def _check_hermes(cfg: Config) -> tuple[str, str]:
    try:
        key = read_hermes_api_key(cfg.hermes_env)
    except (OSError, RuntimeError):
        return FAIL, (f"API_SERVER_KEY is not set in {cfg.hermes_env}. "
                      "Enable the Hermes API server (see README, step 1).")
    req = urllib.request.Request(f"{cfg.hermes_url}/health", headers={"Authorization": f"Bearer {key}"})
    try:
        with urllib.request.urlopen(req, timeout=3) as r:
            return OK, f"Hermes API server answers at {cfg.hermes_url} (HTTP {r.status})"
    except Exception as e:  # noqa: BLE001 - any failure means "not reachable", report it
        return FAIL, (f"Hermes API server not reachable at {cfg.hermes_url} ({e}). "
                      "Is the Hermes gateway running? Try: hermes gateway status")


def _check_service() -> tuple[str, str]:
    if not shutil.which("systemctl"):
        return WARN, "systemd not found; run `hermes-remote-bridge serve` yourself"
    r = subprocess.run(["systemctl", "--user", "is-active", "hermes-remote-bridge"],
                       capture_output=True, text=True)
    state = r.stdout.strip() or "unknown"
    if state == "active":
        return OK, "Bridge service is running"
    return FAIL, (f"Bridge service is {state}. Start it: systemctl --user enable --now hermes-remote-bridge "
                  "(logs: journalctl --user -u hermes-remote-bridge)")


def _check_network(cfg: Config) -> tuple[str, str]:
    if not cfg.lan:
        return WARN, "Local network is off (lan = false in config.toml); only Tailscale is served"
    trust = TrustStore(cfg.trust_file)
    net = current_network()
    if net is None:
        return WARN, "Can't identify the current network (NetworkManager not found or no default route)"
    if not trust.is_trusted(net):
        return WARN, (f"Network '{net.name}' is not trusted, so the phone can't connect over Wi-Fi here. "
                      "If this is your home or office: hermes-remote-bridge trust")
    ips = serving_lan_ips(cfg.lan, trust)
    return OK, f"Trusted network '{net.name}', serving https://{ips[0] if ips else '?'}:{cfg.port}"


def _check_firewall(cfg: Config) -> tuple[str, str]:
    s = firewall.state(cfg.port)
    if s.kind == "none" or not s.active:
        return OK, "No active firewall blocks the bridge port"
    if s.port_open:
        return OK, f"{s.kind}: port {cfg.port} is allowed from private networks"
    if s.port_open is None:
        return WARN, f"{s.kind} is active; can't read its rules without root. Run: hermes-remote-bridge firewall"
    return FAIL, f"{s.kind} blocks port {cfg.port} from the local network. Fix: hermes-remote-bridge firewall"


def _check_tailscale() -> tuple[str, str]:
    try:
        ips, owner = sync_tailscale_ips()
    except Exception:  # noqa: BLE001 - Tailscale is optional
        return WARN, "Tailscale not running (optional; needed only away from trusted Wi-Fi)"
    v4 = [ip for ip in ips if "." in ip]
    return OK, f"Tailscale up ({', '.join(v4) or 'no IPv4'}; owner {owner or 'unknown'})"


def _check_devices(cfg: Config) -> tuple[str, str]:
    active = [d for d in DeviceStore(cfg.devices_file).list() if not d.revoked_at]
    if not active:
        return WARN, "No paired devices yet. Pair one: hermes-remote-bridge pair phone"
    return OK, f"{len(active)} paired device(s): {', '.join(d.name for d in active)}"


def run(cfg: Config) -> int:
    checks = [_check_hermes(cfg), _check_service(), _check_network(cfg), _check_firewall(cfg),
              _check_tailscale(), _check_devices(cfg)]
    for level, msg in checks:
        print(f" {_MARK[level]} {msg}")
    failed = sum(level == FAIL for level, _ in checks)
    print("\nAll good." if not failed else f"\n{failed} problem(s) found.")
    return 1 if failed else 0
