"""Run the real bridge against the demo's fake backends.

This is what ``demo/run-demo.sh`` starts instead of ``hermes-remote-bridge serve``. It is the
production bridge, unmodified, with the seams below replaced so a demo can never touch or leak
the real machine:

  * hermes serve          -> demo/mock_hermes.py        (found through the demo's spawn ledger)
  * the Tailscale socket  -> demo/fake_tailscale.py     (invented host, peers and IPs)
  * the PC's Wi-Fi name the status cards read from the host
  * the `hermes` CLI      -> demo/fake-hermes-cli        (approvals.mode)

Everything else - pairing, device tokens, TLS and its pin, rate limits, the audit log, the relay
and its method allowlist - is the real implementation.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "bridge"))
sys.path.insert(0, str(ROOT / "demo"))

import fake_tailscale  # noqa: E402
from hermes_remote_bridge import app as bridge_app, cli, network, tailnet  # noqa: E402


def _sanitise_identity() -> None:
    """Replace the real-machine value the status screens would otherwise show.

    The connection card shows the current network's name. It comes from the host, not from
    configuration, so a demo would otherwise publish the owner's Wi-Fi name in a screenshot.
    """
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
