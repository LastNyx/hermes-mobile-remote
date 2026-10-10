#!/usr/bin/env python3
"""Record the demo screenshots by driving the app over adb.

Start the demo stack first, pair the app, then:

    python3 demo/capture_screenshots.py [--out demo/screenshots] [--speed 3]

Every frame comes from the synthetic demo backend (demo/mock_hermes.py), so a real session,
hostname or model name can never end up in a published screenshot. Coordinates are read from
the live view hierarchy instead of hardcoded, so this survives layout changes.
"""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = "io.github.nideta231.hermesremote"


def find_adb() -> str:
    for candidate in (shutil.which("adb"),
                      Path.home() / ".loveconnect-tools/android-sdk/platform-tools/adb"):
        if candidate and Path(candidate).exists():
            return str(candidate)
    sys.exit("adb not found: put it on PATH or set ANDROID_HOME")


ADB = ""


def adb(*args: str) -> bytes:
    return subprocess.run([ADB, *args], capture_output=True).stdout


def adb_text(*args: str) -> str:
    return adb(*args).decode(errors="replace")


def shot(path: Path) -> None:
    path.write_bytes(adb("exec-out", "screencap", "-p"))
    print(f"  {path.name}")


def ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb_text("shell", "cat", "/sdcard/ui.xml")


def nodes(xml: str) -> list[tuple[int, int, int, int, str, str]]:
    """(x1, y1, x2, y2, content-desc, class) for every node with bounds."""
    out = []
    for m in re.finditer(r"<node[^>]*>", xml):
        tag = m.group(0)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not b:
            continue
        desc = re.search(r'content-desc="([^"]*)"', tag)
        cls = re.search(r'class="([^"]*)"', tag)
        out.append((*map(int, b.groups()), desc.group(1) if desc else "", cls.group(1) if cls else ""))
    return out


def tap(x: int, y: int, pause: float = 1.2) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(pause)


def tap_node(predicate, pause: float = 1.2) -> bool:
    for x1, y1, x2, y2, desc, cls in nodes(ui()):
        if predicate(x1, y1, x2, y2, desc, cls):
            tap((x1 + x2) // 2, (y1 + y2) // 2, pause)
            return True
    return False


def composer_box() -> tuple[int, int, int, int]:
    for x1, y1, x2, y2, _desc, cls in nodes(ui()):
        if cls.endswith("EditText"):
            return (x1, y1, x2, y2)
    sys.exit("composer (EditText) not found on screen")


def tap_composer(pause: float = 1.2) -> None:
    x1, y1, x2, y2 = composer_box()
    tap((x1 + x2) // 2, (y1 + y2) // 2, pause)


def tap_send(pause: float = 0.4) -> None:
    """The send button: the clickable node right of the composer, vertically inside it."""
    cx1, cy1, cx2, cy2 = composer_box()
    for x1, y1, x2, y2, _desc, cls in nodes(ui()):
        if x1 >= cx2 and cy1 - 40 <= y1 and y2 <= cy2 + 40 and (x2 - x1) < 260:
            tap((x1 + x2) // 2, (y1 + y2) // 2, pause)
            return
    sys.exit("send button not found next to the composer")


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace(" ", "%s"))


def clear_composer() -> None:
    for _ in range(80):
        adb("shell", "input", "keyevent", "67")


TABS = ("Agent", "Sessions", "Desktop", "System")


def tab(index: int, pause: float = 2.0) -> None:
    """Tap a bottom-navigation tab by its label (1=Agent .. 4=System)."""
    want = TABS[index - 1]
    xml = ui()
    for m in re.finditer(r"<node[^>]*>", xml):
        tag = m.group(0)
        if f'text="{want}"' not in tag:
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            tap((x1 + x2) // 2, (y1 + y2) // 2, pause)
            return
    sys.exit(f"tab {want!r} not found in the bottom bar")


def is_working() -> bool:
    """True while the app shows a running/streaming state."""
    xml = ui()
    return any(word in xml for word in ("Working", "Steer the running task", "Waiting"))


def wait_until_idle(timeout: float = 90.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if not is_working():
            return
        time.sleep(1.0)


def send_and_watch(prompt: str, out: Path, mid_shot: str, mid_after: float = 3.0) -> None:
    """Send a prompt, grab the screen mid-run, then wait for the reply to finish."""
    tap_composer()
    type_text(prompt)
    time.sleep(1.0)
    tap_send()
    time.sleep(mid_after)
    shot(out / mid_shot)
    wait_until_idle()
    time.sleep(1.0)


def main() -> None:
    global ADB
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "demo" / "screenshots"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    ADB = find_adb()

    print(f"Recording into {out}")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(5)
    adb("shell", "input", "keyevent", "111")   # hide the keyboard so the first frame is clean
    time.sleep(1)

    print("1. empty chat")
    shot(out / "01-chat-empty.png")

    print("2. a question, with a tool call and a streamed reply")
    send_and_watch("How does the bridge authenticate a phone?", out, "02-chat-running.png")
    shot(out / "03-chat-answered.png")

    print("3. a command that needs approval")
    send_and_watch("Run the test suite and fix whatever fails", out, "04-approval.png", mid_after=2.5)
    shot(out / "05-after-approval.png")

    print("4. slash commands")
    tap_composer()
    adb("shell", "input", "text", "/")
    time.sleep(2)
    shot(out / "06-slash-commands.png")
    clear_composer()
    adb("shell", "input", "keyevent", "111")
    time.sleep(1)

    print("5. model picker")
    tap_node(lambda x1, y1, x2, y2, d, c: y1 < 600 and 100 < y1 and c.endswith("View"), pause=2.0)
    shot(out / "07-model-picker.png")
    tap(540, 120, pause=2.0)   # tap the scrim: dismisses the sheet, stays in the app

    print("6. sessions, system")
    tab(2)
    shot(out / "08-sessions.png")
    tab(4)
    shot(out / "10-system.png")
    tab(1)

    for leftover in out.glob(".frame-*.png"):
        leftover.unlink()
    print("Done.")


if __name__ == "__main__":
    main()
