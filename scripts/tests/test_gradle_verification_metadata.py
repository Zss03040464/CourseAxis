import unittest
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "android/gradle/verification-metadata.xml"
NAMESPACE = {"v": "https://schema.gradle.org/dependency-verification"}


class GradleVerificationMetadataTests(unittest.TestCase):
    def test_aapt2_checksums_cover_supported_ci_platforms(self) -> None:
        root = ElementTree.parse(METADATA).getroot()
        component = root.find(
            ".//v:component[@group='com.android.tools.build']"
            "[@name='aapt2'][@version='8.13.0-13719691']",
            NAMESPACE,
        )
        self.assertIsNotNone(component)

        artifacts = {
            artifact.attrib["name"]: artifact.find("v:sha256", NAMESPACE).attrib["value"]
            for artifact in component.findall("v:artifact", NAMESPACE)
        }
        self.assertEqual(
            artifacts,
            {
                "aapt2-8.13.0-13719691-linux.jar": (
                    "c1aebd96a144313da65de675cc1f59041b41a52e844228d311bb580ed830b0d9"
                ),
                "aapt2-8.13.0-13719691-osx.jar": (
                    "29213e18381a5d8c72932f8bbd06349f99131ec3b13c14e8c0ec90738b865ca1"
                ),
                "aapt2-8.13.0-13719691.pom": (
                    "ad80277efc576ec7f0a71afe4d1d7b2b7dd7a7d486a3f709a2c5987ae9205854"
                ),
            },
        )


if __name__ == "__main__":
    unittest.main()
