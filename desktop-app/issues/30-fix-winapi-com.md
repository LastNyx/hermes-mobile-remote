# 30-fix: winapi COM threading, enumeration wrapping, exception safety

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: review
Round: 1
Blocked by: 30
Terms: default route, router MAC, connection profile, comtypes
Seam: `bridge/hermes_remote_bridge/host/winapi.py` and `bridge/hermes_remote_bridge/host/windows.py` (`service_state`), tests in `bridge/tests/test_winapi.py` (new) and `bridge/tests/test_host.py`.
Test command: cd bridge && uv run pytest -q
Verify: Planner: suite green; `cargo`-free. Real Windows run stays the user's/CI check.

**Blocked by:** 30
**Status:** todo

Found by the planner (Sonnet Win32/COM review of 30, confirmed in the code): the ctypes half is right
(struct sizes 28/104/88/168 and offsets match the SDK); the COM half would fail on Windows.

## What to build

1. **COM per thread.** The bridge calls these from `asyncio.to_thread` workers; comtypes only initialises COM on the
   thread that imported it. Add a `_com()` context manager (`comtypes.CoInitializeEx(0)` ... `gc.collect()`;
   `comtypes.CoUninitialize()`) and run `network_profile` and every firewall function inside it. Return only plain
   Python values (str/bool/dict/list) built inside the block; no COM object may outlive it.
2. **Wrap enumerated items.** Items from `nlm.GetNetworkConnections()` and `policy.Rules` arrive as bare
   `POINTER(IDispatch)`. Wrap each with `comtypes.client.GetBestInterface(item)` before calling `GetAdapterId`,
   `GetNetwork`, `Name`, ... (same for `conn.GetNetwork()`). Prefer a connection with `GetConnectivity() != 0`.
3. **Never raise.** A `_safe(default)` decorator (catches `Exception`, logs at debug) on every public function:
   `default_routes` -> [], `neighbor_mac` -> "", `network_profile` -> None, `firewall_profiles_enabled` -> False,
   `firewall_rules` -> [], `bridge_reachable` -> False. Also an `ImportError` for comtypes.
4. **Default route parity.** When `GetIpInterfaceEntry` fails, use interface metric 0 and keep the route (the old
   PowerShell did), instead of skipping it.
5. **Gateway MAC.** Skip neighbor rows whose MAC is all zero and keep looking.
6. **service_state.** `bridge_reachable(port)` takes the configured port (`Config.load().port`, default 8650), and
   `service_state` returns `"active"` or `"inactive"` (not `"unknown"`) for the bridge; check that doctor.py's
   branches read well for `inactive`.
7. `ConvertInterfaceLuidToAlias(..., c.c_size_t(257))`; update the windows.py module docstring (ctypes/COM via
   winapi.py, PowerShell only in run_elevated until ticket 31).

## Acceptance criteria

- [ ] Struct sizes and the key offsets asserted on Linux (`sizeof`, `Struct.field.offset`)
- [ ] IPv4 byte order and the neighbor state/zero-MAC filtering tested by patching the row source
- [ ] A raising COM/ctypes stub makes each public function return its default (one test per function)
- [ ] network_profile through a fake `comtypes` module in `sys.modules`: wraps items, enters `_com()`, returns
      `({GUID}, name)`; firewall_rules likewise
- [ ] service_state active/inactive with the configured port; existing tests green

## Result
Commit aaed0c9. `cd bridge && uv run pytest -q`: 190 passed.
Tests: struct sizes/offsets (test_struct_sizes_and_key_offsets); IPv4 byte order, metric-0 fallback, neighbor state/zero-MAC (test_default_routes_*, test_neighbor_mac_*); never-raise, one per function (parametrized test_public_functions_return_default_*); fake comtypes for network_profile and firewall_rules (wrap, _com order init/create/uninit, GetConnectivity preference); service_state active/inactive with port 9123 (test_host.py).
Reuse: nothing existing for COM/_safe; Config.load().port reused (lazy import, circular). doctor.py reads fine for "inactive" (Windows branch checks the port and prints a start hint).
Item 7: c_size_t(257) and windows.py docstring done. Corrected my offset guess: ForwardRow NextHop is 44, Metric 84, IfRow Metric 148 (sizes still 28/104/88/168).
Notes added: 1.
Not checked: real Windows COM/ctypes run; real Config.load() port on a Windows host.

Planner Verify: PASS code shows _com() around COM, GetBestInterface wrapping, @_safe on public functions; suite green. Real Windows still a user/CI check
