import unittest

import ufi_setup


class HardwareProfileTests(unittest.TestCase):
    def test_exact_profile_match(self) -> None:
        report = {
            "productDevice": "msm8916_32_512",
            "androidSdk": 19,
            "baseband": "UFI003_CT 20220903",
            "usbId": "05c6:90b4",
            "lanHost": "192.168.100.1",
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
        }
        profile, notes = ufi_setup.match_profile(report, ufi_setup.load_profiles())
        self.assertIsNone(profile)
        self.assertIn("baseband family differs", notes)
        self.assertIn("USB ID differs", notes)


if __name__ == "__main__":
    unittest.main()
