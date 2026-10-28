# 33-fix: tray "Pair a device" navigation is tested and its listener removed on unmount

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: done
Round: 2
Blocked by: 33
Terms: tray
Seam: `desktop/src/App.vue` (the `navigate` window listener) and `desktop/src/ui.test.js`.
Test command: cd desktop && bash test.sh
Verify: Planner: suite green.

## What to build

Mount the app in vitest, dispatch a `navigate` event with detail `pair`, and assert the Pair tab is shown; an unknown id leaves the tab unchanged. Remove the listener when the app unmounts.

## Acceptance criteria

- [ ] `navigate` with `pair` switches to the Pair tab (mounted App)
- [ ] An unknown page id is ignored
- [ ] The listener is removed on unmount

## Result

Planner Verify: PASS planner fixed directly (413aba2, trivial lane)
