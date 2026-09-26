from pathlib import Path
import contextlib
import io
import tempfile
import unittest
from unittest import mock

import ufi_sms


class SmsDurabilityTests(unittest.TestCase):
    def message(self, index: int) -> ufi_sms.SmsMessage:
        return ufi_sms.SmsMessage(
            storage="ME",
            index=index,
            status="REC READ",
            sender="+998900000000",
            timestamp=f"2026-09-25 12:00:{index:02d}",
            body="message-" + str(index) + "-" + "x" * 80,
            raw_pdu=f"AA{index:02x}",
        )

    def test_private_archive_rotates_and_remains_readable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inbox.jsonl"
            for index in range(6):
                ufi_sms.append_archive(path, self.message(index), maximum_bytes=350, backups=2)
            messages, keys = ufi_sms.load_archive(path, backups=2)
            self.assertTrue(path.exists())
            self.assertTrue(path.with_name("inbox.jsonl.1").exists())
            self.assertLessEqual(len(messages), 6)
            self.assertEqual(messages[-1].index, 5)
            self.assertIn(ufi_sms.message_key(self.message(5)), keys)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_polling_intervals_are_bounded(self) -> None:
        parser = ufi_sms.build_parser()
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                parser.parse_args(["watch", "--interval", "0"])
            with self.assertRaises(SystemExit):
                parser.parse_args(["watch", "--telegram-retry", "0"])

    def test_pending_state_is_bounded_with_matching_keys(self) -> None:
        messages = [self.message(index) for index in range(5)]
        archived = {ufi_sms.message_key(message) for message in messages}
        with mock.patch.object(ufi_sms, "MAX_PENDING_MESSAGES", 3):
            ufi_sms.trim_pending_state(messages, archived)
        self.assertEqual([message.index for message in messages], [2, 3, 4])
        self.assertEqual(archived, {ufi_sms.message_key(message) for message in messages})


if __name__ == "__main__":
    unittest.main()
