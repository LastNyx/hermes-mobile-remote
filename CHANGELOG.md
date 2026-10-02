# Changelog

Releases are git tags; the version in the app comes from the tag. `git log v0.9.0..vX.Y.Z` is the
authoritative history — this file only summarises what is worth knowing before upgrading.

## v0.9.0

Mobile-first UI overhaul. Navigation became a bottom bar (Agent, Sessions, Desktop, System); on
phones the session list is a modal drawer, and at 720 dp and wider it is a fixed pane beside the
chat.

## v0.8.1

Packaging fix for the release workflow: `apksigner` labels its output `Signer #1` in some
build-tools versions and `V2 Signer` in others, so the verification step now matches on the
certificate digest instead of the label.

## v0.8.0

- Reasoning-effort picker, sent per run as `model_options.reasoning_effort`.
- Slash commands and skills, executed on the PC through Hermes' TUI gateway.
- In-app updates from tagged GitHub Releases, with the APK digest and signing certificate checked.
- The application id became `io.github.nideta231.hermesremote`.

## Earlier

The first public commit: the Android app, the bridge, the installer, and the LAN-over-pinned-TLS
and Tailscale connection model described in [docs/SECURITY.md](docs/SECURITY.md).
