"""A fake Tailscale LocalAPI for the demo, served on a Unix socket.

The bridge reads Tailscale state through ``/run/tailscale/tailscaled.sock``. In the demo that
would report the real machine's hostname and the real names of every paired device, which must
never end up in a public screenshot. This module serves the two endpoints the bridge uses
(``/localapi/v0/status`` and ``/localapi/v0/whois``) with invented data, and
``demo/serve.py`` points the bridge at this socket instead of the real one.

Nothing here is a Tailscale implementation: it is just enough for the bridge's status page and
its peer-identity check.
"""
from __future__ import annotations

import json
import os
import socketserver
import sys
import threading
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

# Invented, documentation-range values only: 100.64.0.0/10 is CGNAT space Tailscale uses.
SELF_HOST = "demo-pc"
DNS_NAME = "demo-pc.tail0000.ts.net"
SELF_IPS = ["100.64.0.10", "fd7a:115c:a1e0::a00:27ff:fe4e:66a1"]
OWNER_LOGIN = "demo-owner@example.com"
PEERS = {
    "n1": {"HostName": "demo-phone", "OS": "android", "Online": True},
    "n2": {"HostName": "demo-tablet", "OS": "android", "Online": False},
}

STATUS = {
    "BackendState": "Running",
    "Self": {
        "HostName": SELF_HOST,
        "DNSName": DNS_NAME + ".",
        "TailscaleIPs": SELF_IPS,
        "UserID": 100,
    },
    "User": {"100": {"LoginName": OWNER_LOGIN}},
    "Peer": PEERS,
}


class _Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):  # noqa: A002
        if os.environ.get("DEMO_VERBOSE"):
            print("[fake-tailscale]", format % args)

    def _json(self, obj, code: int = 200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        url = urlparse(self.path)
        if url.path == "/localapi/v0/status":
            return self._json(STATUS)
        if url.path == "/localapi/v0/whois":
            addr = (parse_qs(url.query).get("addr") or [""])[0]
            host = addr.rsplit(":", 1)[0].strip("[]")
            if host not in SELF_IPS:
                return self._json({"error": "not a tailnet address"}, 404)
            return self._json({
                "UserProfile": {"LoginName": OWNER_LOGIN},
                "Node": {"Name": SELF_HOST, "ComputedName": SELF_HOST,
                         "Hostinfo": {"OS": "linux"}},
            })
        return self._json({"error": "not found"}, 404)


class _UnixServer(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True
    allow_reuse_address = True


def serve(socket_path: str) -> _UnixServer:
    """Start the fake LocalAPI on a background thread; returns the server."""
    if os.path.exists(socket_path):
        os.unlink(socket_path)
    os.makedirs(os.path.dirname(socket_path), exist_ok=True)
    server = _UnixServer(socket_path, _Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


if __name__ == "__main__":
    import time

    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/hermes-demo-tailscaled.sock"
    serve(path)
    print(f"fake Tailscale LocalAPI on {path}", flush=True)
    while True:
        time.sleep(3600)
