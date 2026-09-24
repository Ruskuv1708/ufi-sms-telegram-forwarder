from pathlib import Path
import tempfile
import unittest

from desktop.model import CallHistory, group_conversations, normalize_number


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


if __name__ == "__main__":
    unittest.main()
