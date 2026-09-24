"""Pure data helpers shared by the desktop UI and its tests."""

from __future__ import annotations

from collections import OrderedDict
import json
import os
from pathlib import Path
import time
from typing import Any


def normalize_number(value: str, minimum_digits: int = 3) -> str:
    cleaned: list[str] = []
    for character in value.strip():
        if character.isascii() and character.isdigit():
            cleaned.append(character)
        elif character == "+" and not cleaned:
            cleaned.append(character)
        elif character in " -().":
            continue
        else:
            return ""
    result = "".join(cleaned)
    digits = len(result) - (1 if result.startswith("+") else 0)
    return result if minimum_digits <= digits <= 20 else ""


def group_conversations(messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: OrderedDict[str, dict[str, Any]] = OrderedDict()
    for message in messages:
        address = str(message.get("address", ""))
        if address not in grouped:
            grouped[address] = {"address": address, "latest": message, "messages": [], "unread": 0}
        conversation = grouped[address]
        conversation["messages"].append(message)
        if int(message.get("type", 0)) == 1 and not bool(message.get("read", True)):
            conversation["unread"] += 1
    return list(grouped.values())


class CallHistory:
    """Track gateway state transitions and retain the latest 100 calls."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.entries: list[dict[str, Any]] = []
        self.active: dict[str, Any] | None = None
        self.previous_state = "IDLE"
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            if isinstance(raw, list):
                self.entries = [item for item in raw if isinstance(item, dict)][:100]
        except (OSError, ValueError, json.JSONDecodeError):
            self.entries = []

    def observe(self, status: dict[str, Any], now_ms: int | None = None) -> bool:
        now = now_ms if now_ms is not None else int(time.time() * 1000)
        state = str(status.get("state", "IDLE"))
        number = str(status.get("caller", ""))
        direction = str(status.get("direction", "")).upper()
        changed = False
        if state in ("RINGING", "DIALING", "ACTIVE") and self.active is None:
            if direction not in ("INCOMING", "OUTGOING"):
                direction = "INCOMING" if state == "RINGING" else "OUTGOING"
            self.active = {
                "id": str(now),
                "number": number,
                "direction": direction,
                "startedAt": now,
                "connectedAt": now if state == "ACTIVE" else 0,
                "endedAt": 0,
                "outcome": "In progress",
            }
            changed = True
        elif self.active is not None and number and not self.active.get("number"):
            self.active["number"] = number
            changed = True
        if self.active is not None and state == "ACTIVE" and not self.active["connectedAt"]:
            self.active["connectedAt"] = now
            changed = True
        if self.active is not None and state == "IDLE":
            self.active["endedAt"] = now
            if self.active["connectedAt"]:
                self.active["outcome"] = "Completed"
            elif self.active["direction"] == "INCOMING":
                self.active["outcome"] = "Missed"
            else:
                self.active["outcome"] = "Not connected"
            self.entries.insert(0, self.active)
            self.entries = self.entries[:100]
            self.active = None
            self._save()
            changed = True
        self.previous_state = state
        return changed

    def _save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.entries, indent=2) + "\n", encoding="utf-8")
        if os.name != "nt":
            temporary.chmod(0o600)
        os.replace(temporary, self.path)
