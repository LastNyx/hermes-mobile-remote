# 02-fix: One pairing-URI and QR-PNG helper

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: done
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

## Result

Commit 2ad0bd4. `cd bridge && uv run pytest -q`: 178 passed.
Refactor only; no new tests (existing tests are the check, per ticket).
- Criterion 1: added `local_api.pairing_uri`; `cmd_pair` and `/v1/local/pair` both use `pairing_uri` + `qr_png`; removed duplicate segno code and unused `urllib.parse` imports. `cmd_devices` now creates one `DeviceStore`.
- Criterion 2: the existing pair and local API tests pass unchanged.
Reused: `local_api.qr_png`, `pairing_payload`. Notes added: 1 line.
Not checked: nothing

Planner Verify: SKIP refactor only; suite green
