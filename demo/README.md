# The demo

A complete Hermes Remote stack that needs no Hermes, no API keys and no model calls. The bridge,
the pairing, TLS pinning, device tokens, rate limits and the socket relay are all the real
implementation; only the things behind it are fake.

That makes it useful for three things:

- **Trying the app** before you install anything on your PC.
- **Working on the UI** without spending tokens or waiting on a model.
- **Recording screenshots and the demo GIF** against data that cannot leak anything private.

## Run it

```bash
(cd bridge && uv sync)     # once
./demo/run-demo.sh --pair
```

```
Demo is up.

  bridge   https://192.168.1.10:8765   (pinned HTTPS, trusted local network)
           http://127.0.0.1:8765       (loopback)
  state    .../demo/.state
```

Scan the printed QR code in the app, or enter the URL and token by hand. Then send a message.

Try the words *run*, *shell*, *command*, *delete* or *install* in a prompt: the scripted agent
asks for approval first, which is how the screenshots show the approval dock.

`Ctrl-C` stops both processes. Nothing is written outside `demo/.state/`.

## What is real and what is fake

| Piece | In the demo |
|---|---|
| `bridge/hermes_remote_bridge/` | **Real.** Every endpoint, gate, limit and the relay. |
| Android app | **Real.** The release APK talks to this bridge unmodified. |
| Pairing, device tokens, TLS pin | **Real.** |
| `hermes serve` | Fake: [`mock_hermes.py`](mock_hermes.py) — the JSON-RPC socket and REST the bridge uses, with scripted sessions, models and turns. Found through a spawn ledger in `demo/.state`, like the real one. |
| Tailscale | Fake: [`fake_tailscale.py`](fake_tailscale.py) — invented host, IPs and peers. |
| `hermes` CLI | Fake: [`fake-hermes-cli`](fake-hermes-cli) — `approvals.mode`. |
| Slash commands | Fake, inside `mock_hermes.py` — a small invented list with canned output. |
| Your PC's Wi-Fi name | Replaced, so it cannot appear in a screenshot. |

[`serve.py`](serve.py) is the seam: it starts the fakes, points the real bridge at them, and then
runs the ordinary CLI. Nothing in `bridge/` knows the demo exists.

The fake backend serves what the bridge and the app actually use, in the shapes real Hermes
returns: `session.create/resume`, `prompt.submit`, streamed events, an approval request answered by
id, `model.options`, `complete.slash`, and the sessions REST list.

## Options

| Variable | Default | Effect |
|---|---|---|
| `DEMO_PORT` | `8765` | Bridge port. Does not touch your real bridge on 8650. |
| `DEMO_HERMES_PORT` | `8766` | Port of the fake `hermes serve`. |
| `DEMO_SPEED` | `1` | Multiplies every pause in a scripted turn. Use `3` when recording. |
| `DEMO_VERBOSE` | unset | Log the fake backends' requests. |

The demo keeps its own device registry, TLS identity, config and audit log under `demo/.state/`,
which is gitignored. Delete it to start over.

## Record the screenshots

With the demo running and the app paired:

```bash
python3 demo/capture_screenshots.py     # writes demo/screenshots/*.png
python3 demo/make_gif.py                # writes demo/demo.gif (needs ffmpeg)
```

Both drive the app over `adb`, reading the live view hierarchy for coordinates rather than
hardcoding them. `capture_screenshots.py --out DIR` writes elsewhere.

`DEMO_SPEED=3 ./demo/run-demo.sh --pair` is what you want when recording: the reply streams at a
readable pace instead of instantly.

## One value the demo cannot fake

The System screenshot shows the machine's real LAN address, because that is the address the phone
has to reach — a fake one would not route, and the run in the pictures would not be real. It is an
RFC 1918 address that changes with your network and means nothing outside it. Everything
*identifying* is synthetic: the Tailscale hostname and IPs, the Wi-Fi network name, the PC
username, the model names and every session title.

## Why the fakes exist

The screenshots in this repository are published. Recording them against a real Hermes would put
real session titles, a real Tailscale hostname, a real Wi-Fi name and a real model alias into a
public README. Recording them against a fake that is *structurally* identical means the images stay
honest — they show the real app, the real bridge and a real run — while containing nothing that
belongs to anyone.
