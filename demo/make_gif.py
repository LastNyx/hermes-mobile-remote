#!/usr/bin/env python3
"""Record the demo as an animated GIF.

Drives one run in the app against the synthetic demo backend, capturing frames while the reply
streams, then assembles them with ffmpeg into demo/demo.gif.

    ./demo/run-demo.sh --pair          # terminal 1
    python3 demo/make_gif.py           # terminal 2
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import capture_screenshots as cap  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
FRAMES = Path("/tmp/hermes-demo-frames")
OUT = ROOT / "demo" / "demo.gif"
PROMPT = "How does the bridge authenticate a phone?"


def main() -> None:
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found")
    cap.ADB = cap.find_adb()
    if FRAMES.exists():
        shutil.rmtree(FRAMES)
    FRAMES.mkdir(parents=True)

    cap.adb("shell", "am", "force-stop", cap.PKG)
    cap.adb("shell", "am", "start", "-n", f"{cap.PKG}/.MainActivity")
    time.sleep(5)
    cap.adb("shell", "input", "keyevent", "111")
    time.sleep(1)

    cap.shot(FRAMES / "f000.png")
    cap.tap_composer()
    cap.type_text(PROMPT)
    time.sleep(1.0)
    (FRAMES / "f001.png").write_bytes(cap.adb("exec-out", "screencap", "-p"))
    cap.tap_send()

    # Hold each frame a beat longer than the gap, so the reply reads as streaming.
    start = time.time()
    i = 2
    while time.time() - start < 26 and i < 60:
        (FRAMES / f"f{i:03d}.png").write_bytes(cap.adb("exec-out", "screencap", "-p"))
        i += 1
        time.sleep(0.55)
        if i % 6 == 0 and not cap.is_working():
            break
    # A couple of frames of the finished reply.
    for _ in range(3):
        time.sleep(0.5)
        (FRAMES / f"f{i:03d}.png").write_bytes(cap.adb("exec-out", "screencap", "-p"))
        i += 1

    # Frames are captured ~0.55 s apart, so the output has to hold each one for about that long
    # (2 fps). Letting ffmpeg assume a rate of its own makes the reply fly past.
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-framerate", "2", "-i", str(FRAMES / "f%03d.png"),
        "-vf", "scale=420:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer",
        "-loop", "0", str(OUT),
    ], check=True)
    shutil.rmtree(FRAMES)
    size = OUT.stat().st_size / 1024
    print(f"wrote {OUT} ({size:.0f} KB)")


if __name__ == "__main__":
    main()
