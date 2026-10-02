# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private reporting: [Security → Report a vulnerability](https://github.com/nideta231/hermes-mobile-remote/security/advisories/new)
on this repository. If that is unavailable to you, open an issue that says only "security report
available on request" with no details, and a maintainer will arrange a private channel.

Useful reports include: what an attacker can reach, what they need to already have, and how to
reproduce it. A proof of concept is welcome.

## What is in scope

The bridge, the Android app, the installer, and anything in this repository. In particular:

- A way to reach the Hermes API or its key without being a paired device.
- A way to use a paired device's token from somewhere it should not work, or to make one device's
  token act for another.
- A way to talk to the bridge from a network that was not trusted, or to defeat the certificate pin
  on the LAN path.
- Anything that leaks a token, the API key, or conversation content into a log, a screenshot, an
  error message or an update check.
- A way to make the app install an update that is not the one GitHub published.

## What is out of scope

These are documented properties of the design, not vulnerabilities — see
[docs/SECURITY.md](docs/SECURITY.md) for the full threat model:

- A compromised PC, or an attacker who already has your user account. The bridge runs as you and
  reads `~/.hermes/.env`; it is not a sandbox.
- A rooted phone. Keystore-backed token storage raises the cost of theft, it does not prevent it.
- An agent talked into running something harmful. Approvals are a speed bump for runaway commands,
  not a guarantee about judgement.
- The Hermes API itself, reachable on loopback with the key. The bridge gates it; it does not
  contain it.
- Denial of service by a device you paired.

## Response

Expect an acknowledgement within a few days. Fixes for confirmed issues are released as a new tag,
and the advisory is published after the fix is available rather than before, so users can update
first. Credit is offered in the advisory unless you would rather stay anonymous.
