import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
BASH = "/bin/bash" if Path("/bin/bash").exists() else "bash"


class ReleaseScriptsTests(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_platform_versions_are_aligned_for_1_5_0(self) -> None:
        android = self.read("android/app/build.gradle.kts")
        macos = self.read("macos/project.yml")
        self.assertIn('versionName = "1.5.0"', android)
        self.assertIn("versionCode = 13", android)
        self.assertEqual(macos.count("MARKETING_VERSION: 1.5.0"), 2)
        self.assertEqual(macos.count("CURRENT_PROJECT_VERSION: 10"), 2)

    def test_android_release_requires_explicit_signing_environment(self) -> None:
        script = self.read("scripts/build-android-release.sh")
        for variable in (
            "COURSEAXIS_KEYSTORE_FILE",
            "COURSEAXIS_KEYSTORE_PASSWORD",
            "COURSEAXIS_KEY_ALIAS",
            "COURSEAXIS_KEY_PASSWORD",
        ):
            self.assertIn(f': "${{{variable}:?', script)
        self.assertIn('${COURSEAXIS_OUTPUT_DIR:-$ROOT/artifacts}', script)
        self.assertIn('$OUTPUT_ROOT/android/$VERSION-$VERSION_CODE', script)
        self.assertIn('"$COURSEAXIS_KEYSTORE_FILE" == "$ROOT"/*', script)
        self.assertIn("--no-daemon", script)
        self.assertIn("certificate SHA-256 digest", script)
        self.assertNotIn("security find-generic-password", script)
        self.assertNotIn("$HOME/Library/Developer", script)
        self.assertNotIn("1.4.", script)

    def test_macos_release_uses_developer_id_and_notary_profile_from_environment(self) -> None:
        script = self.read("scripts/build-macos-release.sh")
        for variable in (
            "COURSEAXIS_DEVELOPMENT_TEAM",
            "COURSEAXIS_MACOS_SIGNING_IDENTITY",
            "COURSEAXIS_NOTARY_PROFILE",
        ):
            self.assertIn(f': "${{{variable}:?', script)
        self.assertIn("xcodebuild", script)
        self.assertIn("notarytool submit", script)
        self.assertIn("stapler staple", script)
        self.assertIn("codesign --verify", script)
        self.assertIn("spctl", script)
        self.assertNotIn("security find", script)
        self.assertNotIn("/" + "Users/", script)

    def test_obsolete_incomplete_clt_builder_and_plist_are_removed(self) -> None:
        self.assertFalse((ROOT / "scripts/build-macos-clt.sh").exists())
        self.assertFalse((ROOT / "macos/Config/CLTAppInfo.plist").exists())

    def test_release_builders_fail_closed_without_signing_environment(self) -> None:
        environment = {"PATH": os.environ.get("PATH", "/usr/bin:/bin")}
        for relative_path in (
            "scripts/build-android-release.sh",
            "scripts/build-macos-release.sh",
        ):
            result = subprocess.run(
                [BASH, str(ROOT / relative_path)],
                cwd=ROOT,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(result.returncode, 0, relative_path)

    def test_webview_user_agent_uses_the_build_version(self) -> None:
        source = self.read(
            "android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt"
        )
        self.assertIn('" CourseAxis/${BuildConfig.VERSION_NAME}"', source)
        self.assertNotIn("CourseAxis/1.4.4", source)

    def test_release_metadata_hashes_named_artifacts_without_exposing_secrets(self) -> None:
        script = ROOT / "scripts/generate-release-metadata.sh"
        with tempfile.TemporaryDirectory() as temporary:
            temporary_path = Path(temporary)
            artifact = temporary_path / "CourseAxis-1.5.0-test.bin"
            artifact.write_bytes(b"synthetic release artifact\n")
            output = temporary_path / "metadata"
            environment = os.environ.copy()
            environment["COURSEAXIS_KEYSTORE_PASSWORD"] = "must-not-appear"
            result = subprocess.run(
                [BASH, str(script), "--output", str(output), str(artifact)],
                cwd=ROOT,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            expected = hashlib.sha256(artifact.read_bytes()).hexdigest()
            sums = (output / "SHA256SUMS").read_text(encoding="utf-8")
            metadata = (output / "RELEASE-METADATA.txt").read_text(encoding="utf-8")
            self.assertEqual(sums, f"{expected}  {artifact.name}\n")
            self.assertIn("CourseAxis version: 1.5.0", metadata)
            self.assertIn("Android versionCode: 13", metadata)
            self.assertIn("macOS build: 10", metadata)
            self.assertNotIn(str(artifact.parent), sums + metadata)
            self.assertNotIn("must-not-appear", sums + metadata)


if __name__ == "__main__":
    unittest.main()
