import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "check-public-hygiene.py"
SPEC = importlib.util.spec_from_file_location("check_public_hygiene", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
check_public_hygiene = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check_public_hygiene)


class PublicHygieneTests(unittest.TestCase):
    def test_source_ignores_but_public_rejects_generated_web_directories(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generated = (
                "site/.npm-cache/content/private.txt",
                "site/node_modules/package/source.js",
                "site/dist/assets/index.js",
                "site/coverage/report.html",
            )
            for relative in generated:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("/" + "Users/alice/private", encoding="utf-8")

            self.assertEqual(
                check_public_hygiene.validate_tree(root, mode="source"),
                [],
            )
            errors = check_public_hygiene.validate_tree(root, mode="public")
            for directory_name in (".npm-cache", "node_modules", "dist", "coverage"):
                self.assertTrue(
                    any(
                        f"generated directory is forbidden: site/{directory_name}"
                        in error
                        for error in errors
                    ),
                    errors,
                )

    def test_checker_source_does_not_trigger_its_own_internal_markers(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        self.assertEqual(
            check_public_hygiene.text_errors(
                "scripts/check-public-hygiene.py",
                source,
                mode="public",
            ),
            [],
        )

    def test_rejects_private_key_token_and_personal_path(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "README.md").write_text(
                "/" + "Users/alice/project\n"
                + "Authorization: "
                + "Bearer abcdefghijklmnopqrstuvwxyz\n"
                + "-----BEGIN "
                + "PRIVATE KEY-----\n",
                encoding="utf-8",
            )

            errors = check_public_hygiene.validate_tree(root, mode="public")

            self.assertTrue(any("absolute user path" in error for error in errors), errors)
            self.assertTrue(any("Bearer token" in error for error in errors), errors)
            self.assertTrue(any("private key" in error for error in errors), errors)

    def test_public_mode_rejects_internal_process_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            private_paths = (
                "docs/superpowers/private-plan.md",
                "knowledge/tasks/private-task.md",
                "knowledge/incidents/private-incident.md",
            )
            for relative in private_paths:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("internal", encoding="utf-8")

            errors = check_public_hygiene.validate_tree(root, mode="public")

            for relative in private_paths:
                self.assertIn(
                    f"internal process path is forbidden: {relative}",
                    errors,
                )

    def test_source_mode_skips_private_project_history(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            task = root / "knowledge/tasks/private.md"
            task.parent.mkdir(parents=True)
            task.write_text("/" + "Users/alice/private", encoding="utf-8")
            (root / "README.md").write_text("public", encoding="utf-8")

            self.assertEqual(
                check_public_hygiene.validate_tree(root, mode="source"),
                [],
            )

    def test_rejects_sensitive_files_and_exported_schedule(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "release.jks").write_bytes(b"secret")
            (root / "schedule.document.json").write_text("{}", encoding="utf-8")

            errors = check_public_hygiene.validate_tree(root, mode="public")

            self.assertTrue(any("sensitive file" in error for error in errors), errors)
            self.assertTrue(any("exported schedule" in error for error in errors), errors)

    def test_rejects_private_site_configuration_in_source_and_public_modes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            site = root / "site"
            site.mkdir()
            (site / ".env.example").write_text(
                "VITE_GITHUB_URL=\nVITE_SPONSOR_URL=\n",
                encoding="utf-8",
            )
            (site / ".env.production").write_text(
                "VITE_SPONSOR_URL=https://sponsor.example/private\n",
                encoding="utf-8",
            )
            (site / "private-config.json").write_text(
                '{"merchant": "private"}',
                encoding="utf-8",
            )
            (site / "private-config.example").write_text(
                "private",
                encoding="utf-8",
            )

            for mode in ("source", "public"):
                errors = check_public_hygiene.validate_tree(root, mode=mode)
                self.assertTrue(
                    any("site/.env.production" in error for error in errors),
                    errors,
                )
                self.assertTrue(
                    any("site/private-config.json" in error for error in errors),
                    errors,
                )
                self.assertTrue(
                    any("site/private-config.example" in error for error in errors),
                    errors,
                )
                self.assertFalse(
                    any("site/.env.example" in error for error in errors),
                    errors,
                )

    def test_samples_must_be_explicitly_synthetic(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            samples = root / "samples"
            samples.mkdir()
            (samples / "README.md").write_text("完全合成", encoding="utf-8")
            (samples / "schedule.document.json").write_text(
                json.dumps(
                    {
                        "documentId": "real-export",
                        "terms": [{"school": "真实大学"}],
                        "events": [{"teacher": "真实教师"}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            errors = check_public_hygiene.validate_tree(root, mode="public")

            self.assertTrue(any("synthetic schedule sample" in error for error in errors), errors)

    def test_accepts_synthetic_schedule_samples(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            samples = root / "samples"
            samples.mkdir()
            (samples / "README.md").write_text("所有文件均为从零编写的完全合成数据。", encoding="utf-8")
            (samples / "schedule.document.json").write_text(
                json.dumps(
                    {
                        "documentId": "synthetic-courseaxis-demo",
                        "terms": [{"school": "示例大学", "campus": "示例校区"}],
                        "events": [{"teacher": "虚构教师甲", "title": "合成课程甲"}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (samples / "section_times.json").write_text("[]", encoding="utf-8")

            self.assertEqual(
                check_public_hygiene.validate_tree(root, mode="public"),
                [],
            )


if __name__ == "__main__":
    unittest.main()
