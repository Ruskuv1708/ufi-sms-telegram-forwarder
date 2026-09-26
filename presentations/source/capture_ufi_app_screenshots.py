#!/usr/bin/env python3
"""Capture the real UFI Phone desktop UI with synthetic demo data."""

from __future__ import annotations

from datetime import datetime
import os
from pathlib import Path
import subprocess
import sys
import time

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


def capture(app: UfiPhoneApp, page: str, filename: str) -> None:
    app.show_page(page)
    app.root.update_idletasks()
    app.root.update()
    time.sleep(0.18)
    app.root.update()

    width = app.root.winfo_width()
    height = app.root.winfo_height()
    display = os.environ.get("DISPLAY", ":0")
    if "." not in display:
        display += ".0"
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
            "-i",
            display,
            "-frames:v",
            "1",
            "-y",
            str(OUTPUT_DIR / filename),
        ],
        check=True,
    )


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # Keep this capture deterministic and offline. The screenshots exercise the
    # shipped Tk UI; no gateway, private configuration, or customer data is used.
    UfiPhoneApp.poll = lambda self: None  # type: ignore[method-assign]
    app = UfiPhoneApp({"host": "127.0.0.1", "token": "synthetic-demo"})
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
    app.connection_label.configure(text="●  Modem · Ucell · Ready", foreground=GREEN)

    capture(app, "calls", "ufi-app-calls.png")
    capture(app, "messages", "ufi-app-messages.png")
    app.root.destroy()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
