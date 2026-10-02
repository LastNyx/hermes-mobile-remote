"""Run the real bridge against the demo's fake backends.

This is what ``demo/run-demo.sh`` starts instead of ``hermes-remote-bridge serve``. It is the
production bridge, unmodified, with three seams replaced so a demo can never touch or leak the
real machine:

  * the Hermes API        -> demo/mock_hermes.py        (synthetic sessions, models, runs)
  * the Tailscale socket  -> demo/fake_tailscale.py     (invented host, peers and IPs)
  * the PC username and Wi-Fi name the status cards read from the host
  * the `hermes` CLI      -> demo/fake-hermes-cli        (approvals.mode, reasoning effort)
  * the TUI gateway       -> demo/fake-tui-gateway      (slash commands)

Everything else - pairing, device tokens, TLS and its pin, rate limits, the audit log, run
buffering, SSE replay - is the real implementation.
"""
from __future__ import annotations

import getpass
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "bridge"))
sys.path.insert(0, str(ROOT / "demo"))

import fake_tailscale  # noqa: E402
from hermes_remote_bridge import app as bridge_app, cli, network, tailnet  # noqa: E402


def _sanitise_identity() -> None:
    """Replace the two real-machine values the status screens would otherwise show.

    The RDP card offers ``getpass.getuser()`` and the connection card shows the current
    network's name. Both come from the host, not from configuration, so a demo would otherwise
    publish the owner's username and Wi-Fi name in a screenshot.
    """
    getpass.getuser = lambda: "demo-user"

    real_network_info = network.network_info

    def demo_network_info(trust):
        info = real_network_info(trust)
        return {**info, "name": "Demo Wi-Fi", "interface": "wlan0"} if info else None

    network.network_info = demo_network_info
    bridge_app.network_info = demo_network_info


def main() -> None:
    state = ROOT / "demo" / ".state"
    state.mkdir(parents=True, exist_ok=True)
    socket_path = str(state / "tailscaled.sock")

    # Serve the fake LocalAPI first, then point the bridge's Tailscale client at it. Both the
    # default argument and the module constant are patched: TailnetClient() and
    # sync_tailscale_ips() each captured SOCKET as a default value at import time.
    fake_tailscale.serve(socket_path)
    tailnet.SOCKET = socket_path
    original_client = tailnet.TailnetClient
    tailnet.TailnetClient = lambda *a, **kw: original_client(socket_path)
    original_sync = tailnet.sync_tailscale_ips
    tailnet.sync_tailscale_ips = lambda *a, **kw: original_sync(socket_path)
    # cli and app each imported these names directly, so rebind them there too.
    cli.sync_tailscale_ips = tailnet.sync_tailscale_ips
    bridge_app.TailnetClient = tailnet.TailnetClient

    _sanitise_identity()
    cli.main(sys.argv[1:])


if __name__ == "__main__":
    main()
