"""Tailscale LocalAPI access (unix socket; works without root or operator rights)."""
from __future__ import annotations

import ipaddress
import socket
import sys
import time
from pathlib import Path

import httpx

SOCKET = "/run/tailscale/tailscaled.sock"
_CGNAT = ipaddress.ip_network("100.64.0.0/10")
_TS_V6 = ipaddress.ip_network("fd7a:115c:a1e0::/48")


def is_tailnet_ip(addr: str) -> bool:
    try:
        ip = ipaddress.ip_address(addr)
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip in (_CGNAT if ip.version == 4 else _TS_V6)


def is_loopback(addr: str) -> bool:
    try:
        ip = ipaddress.ip_address(addr)
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip.is_loopback


# RFC1918 / link-local / unique-local. A tailnet address is CGNAT 100.64/10, which is NOT
# private, so is_private_lan_ip keeps the two networks strictly disjoint.
_LAN_NETS = (ipaddress.ip_network("10.0.0.0/8"),
             ipaddress.ip_network("172.16.0.0/12"),
             ipaddress.ip_network("192.168.0.0/16"),
             ipaddress.ip_network("169.254.0.0/16"),
             ipaddress.ip_network("fc00::/7"))


def is_private_lan_ip(addr: str) -> bool:
    """True for a routable-on-this-LAN address (never loopback, never tailnet)."""
    try:
        ip = ipaddress.ip_address(addr)
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    if ip.is_loopback or is_tailnet_ip(addr):
        return False
    return any(ip in net for net in _LAN_NETS if net.version == ip.version)


def local_lan_ips() -> list[str]:
    """Private addresses on interfaces that actually carry a default route.

    Docker and other virtual bridges also hold RFC1918 addresses; serving on them is pointless
    (nothing but other containers can route there) and they leak into the address list the app
    offers, so keep only the addresses of default-route interfaces.
    """
    routable = _default_route_ips() if sys.platform.startswith("linux") else None
    found = [ip for name, ips in _interface_ips().items() for ip in ips] if sys.platform.startswith("linux") else []
    if routable is not None and found:
        found = [ip for ip in found if ip in routable]
    if not found:
        # Fallback: the address the kernel would pick to reach the internet, which is the LAN
        # address on a normally routed host. getaddrinfo(gethostname()) is not enough — it
        # returns loopback whenever the hostname resolves through /etc/hosts.
        try:
            probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            probe.connect(("192.0.2.1", 9))  # TEST-NET-1; UDP connect sends no packet
            found = [probe.getsockname()[0]]
            probe.close()
        except OSError:
            return []
    return [ip for ip in found if is_private_lan_ip(ip)]


def _default_route_ips() -> set[str]:
    """Addresses of interfaces that carry a default route, read from /proc/net/route."""
    out: set[str] = set()
    try:
        for line in Path("/proc/net/route").read_text().splitlines()[1:]:
            fields = line.split()
            if len(fields) > 2 and fields[1] == "00000000":  # destination == default
                out.update(_interface_ips().get(fields[0], ()))
    except OSError:
        return set()
    return out


def _interface_ips() -> dict[str, list[str]]:
    """Every interface's IPv4 addresses, keyed by name, via SIOCGIFCONF (no extra dependency)."""
    import ctypes
    import fcntl
    import struct

    size = 8192
    names = bytearray(size)
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            request = struct.pack("iL", size, ctypes.addressof(ctypes.c_char.from_buffer(names)))
            response = fcntl.ioctl(sock.fileno(), 0x8912, request)  # SIOCGIFCONF
        finally:
            sock.close()
    except (OSError, AttributeError, ValueError):
        return {}
    out: dict[str, list[str]] = {}
    step = 40  # struct ifreq on 64-bit Linux: 16-byte name + 24-byte sockaddr
    for off in range(0, max(0, struct.unpack("iL", response)[0]) - step + 1, step):
        entry = names[off:off + step]
        name = bytes(entry[:16]).split(b"\x00")[0].decode(errors="replace")
        try:
            packed = socket.inet_ntoa(bytes(entry[20:24]))
        except OSError:
            continue
        if name and packed not in out.setdefault(name, []):
            out[name].append(packed)
    return out


class TailnetClient:
    def __init__(self, socket_path: str = SOCKET):
        self._client = httpx.AsyncClient(transport=httpx.AsyncHTTPTransport(uds=socket_path),
                                         base_url="http://local-tailscaled.sock", timeout=5.0)
        self._whois_cache: dict[str, tuple[float, dict | None]] = {}

    async def aclose(self) -> None:
        await self._client.aclose()

    async def status(self) -> dict:
        resp = await self._client.get("/localapi/v0/status")
        resp.raise_for_status()
        return resp.json()

    async def status_self_ips(self) -> list[str]:
        return list((await self.status())["Self"].get("TailscaleIPs") or [])

    async def whois(self, addr: str) -> dict | None:
        """Return {"login", "node", "os"} for a tailnet peer, cached for 60s."""
        now = time.monotonic()
        cached = self._whois_cache.get(addr)
        if cached and now - cached[0] < 60:
            return cached[1]
        result = None
        try:
            host = f"[{addr}]" if ":" in addr else addr
            resp = await self._client.get("/localapi/v0/whois", params={"addr": f"{host}:1"})
            if resp.status_code == 200:
                data = resp.json()
                result = {
                    "login": data["UserProfile"]["LoginName"],
                    "node": data["Node"].get("ComputedName") or data["Node"]["Name"],
                    "os": (data["Node"].get("Hostinfo") or {}).get("OS"),
                }
        except (httpx.HTTPError, KeyError, ValueError):
            result = None
        self._whois_cache[addr] = (now, result)
        return result


def sync_tailscale_ips(socket_path: str = SOCKET) -> tuple[list[str], str | None]:
    """Startup helper: this node's Tailscale IPs and the owner's login name."""
    with httpx.Client(transport=httpx.HTTPTransport(uds=socket_path),
                      base_url="http://local-tailscaled.sock", timeout=5.0) as client:
        status = client.get("/localapi/v0/status").raise_for_status().json()
        ips = list(status["Self"]["TailscaleIPs"])
        owner = (status.get("User") or {}).get(str(status["Self"]["UserID"]), {}).get("LoginName")
        return ips, owner
