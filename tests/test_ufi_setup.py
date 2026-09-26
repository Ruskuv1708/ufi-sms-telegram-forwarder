import json
import os
import tempfile
import unittest
from pathlib import Path

import ufi_setup


class HardwareProfileTests(unittest.TestCase):
    def test_exact_profile_match(self) -> None:
        report = {
            "productDevice": "msm8916_32_512",
            "androidSdk": 19,
            "baseband": "UFI003_CT 20220903",
            "usbId": "05c6:90b4",
            "lanHost": "192.168.100.1",
            "telephony": True,
            "microphone": True,
        }
        profile, notes = ufi_setup.match_profile(report, ufi_setup.load_profiles())
        self.assertIsNotNone(profile)
        self.assertEqual(profile["id"], "ufi003-msm8916-android-4.4")
        self.assertEqual(notes, ["exact tested hardware profile"])

    def test_similar_name_does_not_bypass_checks(self) -> None:
        report = {
            "productDevice": "msm8916_32_512",
            "androidSdk": 19,
            "baseband": "UNRELATED_FIRMWARE",
            "usbId": "1234:5678",
            "lanHost": "192.168.100.1",
            "telephony": True,
            "microphone": True,
        }
        profile, notes = ufi_setup.match_profile(report, ufi_setup.load_profiles())
        self.assertIsNone(profile)
        self.assertIn("baseband family differs", notes)
        self.assertIn("USB ID differs", notes)

    def test_missing_required_capability_does_not_match(self) -> None:
        report = {
            "productDevice": "msm8916_32_512",
            "androidSdk": 19,
            "baseband": "UFI003_CT 20220903",
            "usbId": "05c6:90b4",
            "lanHost": "192.168.100.1",
            "telephony": True,
            "microphone": False,
        }
        profile, notes = ufi_setup.match_profile(report, ufi_setup.load_profiles())
        self.assertIsNone(profile)
        self.assertIn("microphone capability is unavailable", notes)


class GuidedSetupTests(unittest.TestCase):
    def setUp(self) -> None:
        self.modem = {
            "serial": "MODEM-1",
            "productModel": "UFI003",
            "androidRelease": "4.4.4",
            "androidSdk": 19,
            "profile": "ufi003-msm8916-android-4.4",
            "support": "tested",
        }
        self.tablet = {
            "serial": "TABLET-1",
            "productModel": "Pixel Tablet",
            "androidRelease": "16",
            "androidSdk": 36,
            "profile": "",
            "support": "unverified",
        }

    def test_single_modem_and_tablet_are_selected_automatically(self) -> None:
        modem, tablet = ufi_setup.select_install_targets([self.tablet, self.modem])
        self.assertEqual(modem["serial"], "MODEM-1")
        self.assertIsNotNone(tablet)
        self.assertEqual(tablet["serial"], "TABLET-1")

    def test_desktop_only_skips_connected_tablet(self) -> None:
        modem, tablet = ufi_setup.select_install_targets(
            [self.modem, self.tablet], desktop_only=True
        )
        self.assertEqual(modem["serial"], "MODEM-1")
        self.assertIsNone(tablet)

    def test_ambiguous_tablets_require_an_explicit_choice(self) -> None:
        second = dict(self.tablet, serial="TABLET-2")
        with self.assertRaisesRegex(ufi_setup.SetupError, "More than one Android tablet"):
            ufi_setup.select_install_targets([self.modem, self.tablet, second])

    def test_unverified_explicit_modem_is_refused(self) -> None:
        with self.assertRaisesRegex(ufi_setup.SetupError, "unverified hardware"):
            ufi_setup.select_install_targets(
                [self.tablet], modem_serial="TABLET-1", desktop_only=True
            )

    def test_pairing_file_is_private_and_does_not_overwrite(self) -> None:
        config = {"host": "192.168.100.1", "token": "a" * 64}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "modem.ufi-phone"
            ufi_setup.write_pairing_file(path, config)
            document = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(document["kind"], ufi_setup.PAIRING_KIND)
            self.assertEqual(document["version"], ufi_setup.PAIRING_VERSION)
            self.assertEqual(document["host"], config["host"])
            self.assertEqual(document["token"], config["token"])
            if os.name != "nt":
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with self.assertRaisesRegex(ufi_setup.SetupError, "already exists"):
                ufi_setup.write_pairing_file(path, config)


if __name__ == "__main__":
    unittest.main()
