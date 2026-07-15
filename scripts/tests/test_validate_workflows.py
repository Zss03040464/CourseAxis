import importlib.util
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/validate-workflows.py"
ANDROID_WORKFLOWS = (
    ".github/workflows/ci.yml",
    ".github/workflows/codeql.yml",
)
SDKMANAGER_EXPLICIT_COMMAND = (
    'SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"\n'
    '          test -x "$SDKMANAGER"\n'
    '          "$SDKMANAGER" "platforms;android-36" "build-tools;36.0.0"'
)
SDKMANAGER_BARE_COMMAND = 'sdkmanager "platforms;android-36" "build-tools;36.0.0"'
SDKMANAGER_STEP_MARKER = "      - name: Install Android SDK 36\n"
SPEC = importlib.util.spec_from_file_location("validate_workflows", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
validate_workflows = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validate_workflows)


class WorkflowValidationTests(unittest.TestCase):
    def copy_configuration(self, destination: Path) -> None:
        shutil.copytree(ROOT / ".github", destination / ".github")

    def test_repository_workflows_are_valid(self) -> None:
        self.assertEqual(validate_workflows.validate_tree(ROOT), [])

    def test_ci_requires_a_pinned_website_job(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/ci.yml"
            text = workflow.read_text(encoding="utf-8")
            start = text.index("  website:\n")
            end = text.index("  server:\n", start)
            workflow.write_text(text[:start] + text[end:], encoding="utf-8")

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("website" in error for error in errors), errors)

    def test_ci_requires_full_site_audit_and_portability_gate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/ci.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8")
                .replace("npm audit --audit-level=high", "npm audit --omit=dev", 1)
                .replace("python3 scripts/validate-site-build.py", "true", 1),
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("npm audit" in error for error in errors), errors)
            self.assertTrue(
                any("validate-site-build.py" in error for error in errors),
                errors,
            )

    def test_dependabot_requires_site_npm_updates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            dependabot = root / ".github/dependabot.yml"
            text = dependabot.read_text(encoding="utf-8")
            start = text.index('  - package-ecosystem: "npm"\n')
            end = text.index('  - package-ecosystem: "gomod"\n', start)
            dependabot.write_text(text[:start] + text[end:], encoding="utf-8")

            errors = validate_workflows.validate_dependabot(root)

            self.assertTrue(any("npm" in error for error in errors), errors)

    def test_rejects_movable_action_reference(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/ci.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace(
                    "actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0",
                    "actions/checkout@v7",
                    1,
                ),
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("40-character commit SHA" in error for error in errors), errors)

    def test_pull_request_workflow_rejects_secret_reference(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/dependency-review.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8")
                + "\n# forbidden: ${{ secrets.RELEASE_TOKEN }}\n",
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("secret reference" in error for error in errors), errors)

    def test_dependency_review_remains_pull_request_only(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/dependency-review.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace(
                    "on:\n  pull_request:",
                    "on:\n  push:\n  pull_request:",
                    1,
                ),
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("pull_request only" in error for error in errors), errors)

    def test_codeql_builds_external_tool_before_tracing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            workflow = root / ".github/workflows/codeql.yml"
            text = workflow.read_text(encoding="utf-8")
            xcodegen_step = (
                "      - name: Build fixed XcodeGen before tracing\n"
                "        if: matrix.language == 'swift'\n"
                "        run: ./scripts/install-xcodegen.sh \"$RUNNER_TEMP/courseaxis-xcodegen\"\n"
            )
            text = text.replace(xcodegen_step, "", 1)
            text = text.replace(
                "      - name: Upload CodeQL results\n",
                xcodegen_step + "      - name: Upload CodeQL results\n",
                1,
            )
            workflow.write_text(text, encoding="utf-8")

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("before CodeQL tracing" in error for error in errors), errors)

    def test_android_sdkmanager_uses_the_runner_android_home(self) -> None:
        for relative in ANDROID_WORKFLOWS:
            with self.subTest(workflow=relative):
                workflow = ROOT / relative
                text = workflow.read_text(encoding="utf-8")
                self.assertIn(SDKMANAGER_EXPLICIT_COMMAND, text)

                errors = validate_workflows.validate_workflow(
                    relative,
                    text.replace(
                        SDKMANAGER_EXPLICIT_COMMAND,
                        SDKMANAGER_BARE_COMMAND,
                        1,
                    ),
                )

                self.assertTrue(
                    any("Android SDK manager" in error for error in errors),
                    errors,
                )

    def test_android_sdkmanager_rejects_every_unapproved_lowercase_reference(self) -> None:
        unapproved_steps = (
            SDKMANAGER_BARE_COMMAND,
            'command sdkmanager "platforms;android-36"',
            'env sdkmanager "platforms;android-36"',
            '"$ANDROID_HOME/tools/bin/sdkmanager" "platforms;android-36"',
        )

        for relative in ANDROID_WORKFLOWS:
            workflow = ROOT / relative
            text = workflow.read_text(encoding="utf-8")
            weakened_cases = {
                "bare block command": text.replace(
                    SDKMANAGER_EXPLICIT_COMMAND,
                    SDKMANAGER_BARE_COMMAND
                    + "\n          "
                    + SDKMANAGER_EXPLICIT_COMMAND,
                    1,
                ),
                **{
                    step: text.replace(
                        SDKMANAGER_STEP_MARKER,
                        f"      - run: {step}\n" + SDKMANAGER_STEP_MARKER,
                        1,
                    )
                    for step in unapproved_steps
                },
            }
            for case, weakened in weakened_cases.items():
                with self.subTest(workflow=relative, case=case):
                    self.assertNotEqual(weakened, text)

                    errors = validate_workflows.validate_workflow(relative, weakened)

                    self.assertTrue(
                        any("bare Android SDK manager" in error for error in errors),
                        errors,
                    )

    def test_dependabot_covers_all_supported_ecosystems(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            dependabot = root / ".github/dependabot.yml"
            dependabot.write_text(
                dependabot.read_text(encoding="utf-8").replace(
                    '  - package-ecosystem: "docker"',
                    '  - package-ecosystem: "missing-docker"',
                    1,
                ),
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("docker" in error for error in errors), errors)

    def test_dependabot_does_not_borrow_directory_from_later_entry(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_configuration(root)
            dependabot = root / ".github/dependabot.yml"
            dependabot.write_text(
                dependabot.read_text(encoding="utf-8")
                .replace(
                    '  - package-ecosystem: "gradle"\n    directory: "/android"',
                    '  - package-ecosystem: "gradle"\n    directory: "/wrong"',
                    1,
                )
                .replace(
                    '  - package-ecosystem: "gomod"\n    directory: "/server"',
                    '  - package-ecosystem: "gomod"\n    directory: "/android"',
                    1,
                ),
                encoding="utf-8",
            )

            errors = validate_workflows.validate_tree(root)

            self.assertTrue(any("gradle" in error for error in errors), errors)

    def test_xcodegen_installer_refuses_existing_destination(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "existing"
            destination.mkdir()
            marker = destination / "do-not-delete.txt"
            marker.write_text("preserve", encoding="utf-8")

            result = subprocess.run(
                [str(ROOT / "scripts/install-xcodegen.sh"), str(destination)],
                capture_output=True,
                check=False,
                text=True,
            )

            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertEqual(marker.read_text(encoding="utf-8"), "preserve")

    def test_xcodegen_installer_has_no_recursive_delete(self) -> None:
        installer = (ROOT / "scripts/install-xcodegen.sh").read_text(encoding="utf-8")
        self.assertNotIn("rm -rf", installer)
        self.assertNotIn("--disable-sandbox", installer)


if __name__ == "__main__":
    unittest.main()
