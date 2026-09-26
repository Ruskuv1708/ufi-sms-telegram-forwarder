import json
import tempfile
import unittest
from pathlib import Path

from desktop.model import CallHistory, group_conversations, normalize_number
from desktop.ufi_phone_desktop import friendly_connection_error, read_pairing_file
from ufi_voice import VoiceError


class DesktopModelTests(unittest.TestCase):
    def test_normalize_number(self) -> None:
        self.assertEqual(normalize_number("+998 (90) 123-45-67"), "+998901234567")
        self.assertEqual(normalize_number("*100#"), "")

    def test_group_conversations_counts_unread(self) -> None:
        messages = [
            {"address": "100", "type": 1, "read": False, "body": "new"},
            {"address": "100", "type": 2, "read": True, "body": "reply"},
            {"address": "200", "type": 1, "read": True, "body": "old"},
        ]
        grouped = group_conversations(messages)
        self.assertEqual([item["address"] for item in grouped], ["100", "200"])
        self.assertEqual(grouped[0]["unread"], 1)

    def test_history_records_completed_and_missed_calls(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            history = CallHistory(Path(directory) / "history.json")
            history.observe({"state": "RINGING", "caller": "123", "direction": "INCOMING"}, 1000)
            history.observe({"state": "ACTIVE", "caller": "123", "direction": "INCOMING"}, 2000)
            history.observe({"state": "IDLE"}, 4000)
            self.assertEqual(history.entries[0]["outcome"], "Completed")
            history.observe({"state": "RINGING", "caller": "456", "direction": "INCOMING"}, 5000)
            history.observe({"state": "IDLE"}, 6000)
            self.assertEqual(history.entries[0]["outcome"], "Missed")

    def test_pairing_file_import(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "modem.ufi-phone"
            path.write_text(
                json.dumps(
                    {
                        "kind": "ufi-phone-pairing",
                        "version": 1,
                        "host": "192.168.100.1",
                        "token": "b" * 64,
                    }
                ),
                encoding="utf-8",
            )
            self.assertEqual(read_pairing_file(path)["host"], "192.168.100.1")

    def test_pairing_file_rejects_an_unknown_format(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "wrong.json"
            path.write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(VoiceError, "not supported"):
                read_pairing_file(path)

    def test_pairing_file_size_is_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "oversized.ufi-phone"
            path.write_bytes(b"x" * (16 * 1024 + 1))
            with self.assertRaisesRegex(VoiceError, "too large"):
                read_pairing_file(path)

    def test_connection_errors_are_actionable(self) -> None:
        self.assertIn(
            "No modem pairing",
            friendly_connection_error("Gateway is not configured; run setup"),
        )
        self.assertIn(
            "updated together",
            friendly_connection_error("Gateway protocol version is incompatible"),
        )


if __name__ == "__main__":
    unittest.main()
