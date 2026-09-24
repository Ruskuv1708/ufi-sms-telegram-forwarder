from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


class AndroidManifestTests(unittest.TestCase):
    def test_standalone_and_gradle_capabilities_stay_in_sync(self) -> None:
        standalone = ET.parse(ROOT / "android-tablet-client" / "AndroidManifest.xml").getroot()
        gradle = ET.parse(
            ROOT / "android-tablet-client" / "AndroidManifest.gradle.xml"
        ).getroot()
        standalone.attrib.pop("package", None)
        self.assertEqual(self.signature(standalone), self.signature(gradle))

    @classmethod
    def signature(cls, node: ET.Element) -> tuple:
        return (
            node.tag,
            tuple(sorted(node.attrib.items())),
            tuple(cls.signature(child) for child in node),
        )


if __name__ == "__main__":
    unittest.main()
