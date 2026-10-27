# 02-fix: One pairing-URI and QR-PNG helper

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 1
Blocked by: 02-app-bridge-local-api.md
Terms: local client
Seam: refactor only, existing tests are the check: `bridge/hermes_remote_bridge/cli.py` (`cmd_pair` ~339-345, `cmd_devices`), `bridge/hermes_remote_bridge/local_api.py`, `bridge/hermes_remote_bridge/app.py` (`local_pair`).
Test command: cd bridge && uv run pytest -q
Verify: None (unit tests are the check).

## What to build

`cmd_pair` still builds the `hermesremote://pair?` URI and writes the QR PNG itself, while `local_api.qr_png` and the `/v1/local/pair` route repeat the same steps. Put the URI building next to `pairing_payload` in `local_api` as one helper. Make `cmd_pair` and the route both use that helper and `qr_png`. Behaviour and output stay identical. Optionally, in `cmd_devices` stop creating `DeviceStore` twice.

## Acceptance criteria

- [ ] The URI and PNG each have one implementation, used by the CLI and the local route
- [ ] Existing pair and local API tests stay green unchanged
