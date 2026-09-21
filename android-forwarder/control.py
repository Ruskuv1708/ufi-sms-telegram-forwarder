#!/usr/bin/env python3
"""Safely configure and inspect the modem-resident SMS forwarder over ADB."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


PACKAGE = "com.ufi.smsforwarder"
COMPONENT = PACKAGE + "/.ControlReceiver"
ACTIONS = {
    "status": PACKAGE + ".STATUS",
    "test": PACKAGE + ".TEST",
    "drain": PACKAGE + ".DRAIN",
}
TOKEN_PATTERN = re.compile(r"^[0-9]+:[A-Za-z0-9_-]+$")


def telegram_config() -> tuple[str, str]:
    path = Path.home() / ".config" / "ufi-sms" / "telegram.json"
    mode = path.stat().st_mode & 0o777
    if mode & 0o077:
        raise RuntimeError(f"Refusing to read {path}: expected private permissions (0600)")
    data = json.loads(path.read_text(encoding="utf-8"))
    token = str(data.get("bot_token", ""))
    chat_id = str(data.get("chat_id", ""))
    if not TOKEN_PATTERN.fullmatch(token) or not chat_id:
        raise RuntimeError(f"Invalid Telegram settings in {path}")
    return token, chat_id


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("configure", "status", "test", "drain"))
    args = parser.parse_args()

    command = [
        "adb",
        "shell",
        "am",
        "broadcast",
        "--include-stopped-packages",
    ]
    if args.command == "configure":
        token, chat_id = telegram_config()
        action = PACKAGE + ".CONFIGURE"
        command.extend(
            [
                "-a",
                action,
                "-n",
                COMPONENT,
                "--es",
                "token",
                token,
                "--es",
                "chat_id",
                chat_id,
            ]
        )
    else:
        command.extend(["-a", ACTIONS[args.command], "-n", COMPONENT])

    result = subprocess.run(command, check=False)
    return result.returncode


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
