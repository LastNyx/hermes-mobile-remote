"""Which local network the PC is on, and whether the user trusts it.

The bridge only serves the local network on networks the user marked as trusted (home, office).
Anywhere else (a cafe, a hotel) it stays on loopback + Tailscale, because a shared Wi-Fi is
exactly where an unknown device may sit on the address the phone remembers.

A network is identified by the NetworkManager connection profile *and* the gateway's MAC
address. The profile alone is an SSID + password; the gateway MAC ties trust to the physical
router, so a hotspot that copies the SSID does not inherit it.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import time
from dataclasses import asdict, dataclass
from pathlib import Path

from .tailnet import _default_route_ips, _interface_ips, is_private_lan_ip


@dataclass(frozen=True)
class Network:
    id: str
    name: str
    interface: str
    lan_ips: tuple[str, ...]


def _default_route() -> tuple[str, str] | None:
    """(interface, gateway IP) of the IPv4 default route with the lowest metric."""
    best: tuple[int, str, str] | None = None
    try:
        for line in Path("/proc/net/route").read_text().splitlines()[1:]:
            f = line.split()
            if len(f) < 7 or f[1] != "00000000":
                continue
            gw = ".".join(str(b) for b in bytes.fromhex(f[2])[::-1])
            metric = int(f[6])
            if best is None or metric < best[0]:
                best = (metric, f[0], gw)
    except (OSError, ValueError):
        return None
    return (best[1], best[2]) if best else None


def _gateway_mac(gateway: str) -> str | None:
    try:
        for line in Path("/proc/net/arp").read_text().splitlines()[1:]:
            f = line.split()
            if len(f) >= 4 and f[0] == gateway and f[3] != "00:00:00:00:00:00":
                return f[3].lower()
    except OSError:
        pass
    return None


def _nm_connection(interface: str) -> tuple[str, str] | None:
    """(profile UUID, profile name) NetworkManager uses on this interface."""
    if not shutil.which("nmcli"):
        return None
    try:
        out = subprocess.run(["nmcli", "-t", "-f", "GENERAL.CONNECTION,GENERAL.CON-UUID", "device", "show", interface],
                             capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    fields = dict(line.split(":", 1) for line in out.splitlines() if ":" in line)
    uuid, name = fields.get("GENERAL.CON-UUID", ""), fields.get("GENERAL.CONNECTION", "")
    return (uuid, name) if uuid else None


def current_network() -> Network | None:
    """The private network the default route goes through, or None (offline / not private)."""
    route = _default_route()
    if route is None:
        return None
    interface, gateway = route
    lan_ips = tuple(ip for ip in _interface_ips().get(interface, []) if is_private_lan_ip(ip))
    if not lan_ips or not is_private_lan_ip(gateway):
        return None  # e.g. a public IP straight on the interface: never a "local network"
    mac = _gateway_mac(gateway)
    if mac is None:
        return None  # identity unknown yet; the watcher retries
    nm = _nm_connection(interface)
    profile, name = nm if nm else (interface, interface)
    net_id = hashlib.sha256(f"{profile}|{mac}".encode()).hexdigest()[:16]
    return Network(net_id, name or interface, interface, lan_ips)


class TrustStore:
    """Trusted network ids, persisted as JSON (mode 600)."""

    def __init__(self, path: Path):
        self.path = path

    def _read(self) -> dict:
        try:
            return json.loads(self.path.read_text())
        except (OSError, ValueError):
            return {"trusted": {}}

    def _write(self, data: dict) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as fh:
            json.dump(data, fh, indent=2)
        tmp.replace(self.path)

    def is_trusted(self, net: Network | None) -> bool:
        return net is not None and net.id in self._read().get("trusted", {})

    def trust(self, net: Network) -> None:
        data = self._read()
        data.setdefault("trusted", {})[net.id] = {"name": net.name, "since": round(time.time())}
        data.get("declined", {}).pop(net.id, None)
        self._write(data)

    def untrust(self, net_id: str) -> bool:
        data = self._read()
        removed = data.get("trusted", {}).pop(net_id, None) is not None
        if removed:
            self._write(data)
        return removed

    def list(self) -> dict[str, dict]:
        return dict(self._read().get("trusted", {}))

    def declined(self) -> set[str]:
        return set(self._read().get("declined", {}))

    def decline(self, net: Network) -> None:
        data = self._read()
        data.setdefault("declined", {})[net.id] = {"name": net.name, "since": round(time.time())}
        self._write(data)


def serving_lan_ips(enabled: bool, trust: TrustStore) -> list[str]:
    """LAN addresses to serve right now: only on a trusted network, only on its interface."""
    if not enabled:
        return []
    net = current_network()
    if not trust.is_trusted(net):
        return []
    routable = _default_route_ips()
    return [ip for ip in net.lan_ips if not routable or ip in routable]


def network_info(trust: TrustStore) -> dict | None:
    net = current_network()
    if net is None:
        return None
    return {**{k: v for k, v in asdict(net).items() if k != "lan_ips"}, "trusted": trust.is_trusted(net)}
