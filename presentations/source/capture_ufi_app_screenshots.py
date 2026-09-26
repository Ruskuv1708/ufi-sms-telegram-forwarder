#!/usr/bin/env python3
"""Capture the real UFI Phone desktop UI with synthetic demo data."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

SOURCE_DIR = Path(__file__).resolve().parent
WORKSPACE_DIR = SOURCE_DIR.parents[1]
OUTPUT_DIR = WORKSPACE_DIR / "presentations" / "assets"
sys.path.insert(0, str(WORKSPACE_DIR))

from desktop.model import group_conversations  # noqa: E402
from desktop.ufi_phone_desktop import GREEN, UfiPhoneApp  # noqa: E402


def timestamp(hour: int, minute: int) -> int:
    return int(datetime(2026, 9, 24, hour, minute).timestamp() * 1000)


DEMO_HISTORY = [
    {
        "id": "demo-3",
        "number": "Demo desk 01",
        "direction": "INCOMING",
        "startedAt": timestamp(11, 42),
        "connectedAt": timestamp(11, 42),
        "endedAt": timestamp(11, 48),
        "outcome": "Completed",
    },
    {
        "id": "demo-2",
        "number": "Demo field team",
        "direction": "OUTGOING",
        "startedAt": timestamp(10, 16),
        "connectedAt": timestamp(10, 16),
        "endedAt": timestamp(10, 19),
        "outcome": "Completed",
    },
    {
        "id": "demo-1",
        "number": "Demo service line",
        "direction": "INCOMING",
        "startedAt": timestamp(9, 8),
        "connectedAt": 0,
        "endedAt": timestamp(9, 8),
        "outcome": "Missed",
    },
]

DEMO_MESSAGES = [
    {
        "address": "Demo desk 01",
        "body": "Gateway is ready for the pilot demo.",
        "type": 1,
        "read": False,
        "date": timestamp(11, 51),
    },
    {
        "address": "Demo desk 01",
        "body": "Calls and SMS are working on the local network.",
        "type": 2,
        "read": True,
        "date": timestamp(11, 49),
    },
    {
        "address": "Demo field team",
        "body": "Synthetic message for the presentation capture.",
        "type": 1,
        "read": True,
        "date": timestamp(10, 21),
    },
]


def capture(app: UfiPhoneApp, filename: str) -> None:
    app.root.lift()
    app.root.update_idletasks()
    app.root.update()
    app.root.event_generate("<Motion>", warp=True, x=890, y=590)
    # Give the window manager time to settle the oversized requested layouts
    # into the fixed evidence viewport before x11grab resolves its client area.
    time.sleep(0.8)
    app.root.update_idletasks()
    app.root.update()

    width = app.root.winfo_width()
    height = app.root.winfo_height()
    display = os.environ.get("DISPLAY", ":0")
    if "." not in display:
        display += ".0"
    destination = OUTPUT_DIR / filename
    for attempt in range(3):
        subprocess.run(
            [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "x11grab",
                "-window_id",
                str(app.root.winfo_id()),
                "-video_size",
                f"{width}x{height}",
                "-draw_mouse",
                "0",
                "-i",
                display,
                "-frames:v",
                "1",
                "-y",
                str(destination),
            ],
            check=True,
        )
        # A newly mapped XWayland window can briefly yield a valid but entirely
        # black PNG. Real evidence views are comfortably larger than 10 KiB.
        if destination.stat().st_size >= 10_000:
            return
        time.sleep(0.5 * (attempt + 1))
        app.root.lift()
        app.root.update_idletasks()
        app.root.update()
    raise RuntimeError(f"Window capture stayed blank: {destination}")


def demo_app(page: str) -> UfiPhoneApp:
    app = UfiPhoneApp(
        {"host": "127.0.0.1", "token": "synthetic-demo"}, initial_page=page
    )
    app.root.geometry("900x600+40+40")
    app.root.update_idletasks()
    app.history.entries = list(DEMO_HISTORY)
    app.status = {
        "state": "IDLE",
        "caller": "",
        "callReady": True,
        "network": "Ucell",
        "modeRecoveries": 0,
    }
    app.last_state = "IDLE"
    app.messages = list(DEMO_MESSAGES)
    app.conversations = group_conversations(app.messages)
    app.selected_address = "Demo desk 01"
    app.connection_label.configure(
        text="Ucell · Ready", background="#E5F6EC", foreground=GREEN
    )
    if page == "calls":
        app._render_call_status()
        app._render_history()
    elif page == "messages":
        app._render_conversations()
    return app


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--page", choices=("calls", "messages", "setup"))
    parser.add_argument("--filename")
    args = parser.parse_args()
    if bool(args.page) != bool(args.filename):
        parser.error("--page and --filename must be used together")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # Keep this capture deterministic and offline. The screenshots exercise the
    # shipped Tk UI; no gateway, private configuration, or customer data is used.
    UfiPhoneApp.poll = lambda self: None  # type: ignore[method-assign]
    if args.page:
        if args.page == "setup":
            app = UfiPhoneApp(None, "No modem pairing is saved on this computer yet.")
            app.root.geometry("900x600+40+40")
        else:
            app = demo_app(args.page)
        try:
            capture(app, args.filename)
        finally:
            app.root.destroy()
        return 0

    # Tk and XWayland can retain stale capture offsets when multiple root
    # windows are created in one process. Isolate each evidence view so the
    # full client area, including the header, is reproducible.
    for page, filename in (
        ("calls", "ufi-app-calls.png"),
        ("messages", "ufi-app-messages.png"),
        ("setup", "ufi-app-setup.png"),
    ):
        subprocess.run(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "--page",
                page,
                "--filename",
                filename,
            ],
            check=True,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
