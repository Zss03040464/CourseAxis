import hashlib
import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/export-public-candidate.py"
SPEC = importlib.util.spec_from_file_location("export_public_candidate", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
export_public_candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(export_public_candidate)


class PublicCandidateExportTests(unittest.TestCase):
    def git(self, repository: Path, *arguments: str) -> str:
        result = subprocess.run(
            ["git", "-C", str(repository), *arguments],
            capture_output=True,
            check=True,
            text=True,
        )
        return result.stdout.strip()

    def create_repository(self, root: Path) -> tuple[Path, Path]:
        repository = root / "repo"
        package = repository / "CourseAxis"
        package.mkdir(parents=True)
        self.git(repository, "init", "--quiet")
        self.git(repository, "config", "user.name", "Fixture")
        self.git(repository, "config", "user.email", "fixture@example.invalid")

        for relative in sorted(export_public_candidate.REQUIRED_PUBLIC_PATHS):
            path = package / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"committed {relative}\n", encoding="utf-8")

        executable = package / "scripts/tool.sh"
        executable.parent.mkdir(parents=True, exist_ok=True)
        executable.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
        executable.chmod(0o755)

        private_files = {
            "MACOS_BUILD_REPORT.md": "private report",
            "PACKAGE_MANIFEST.md": "private manifest",
            "design-qa.md": "private qa",
            "docs/superpowers/private-plan.md": "private plan",
            "knowledge/tasks/private-task.md": "private task",
            "knowledge/incidents/private-incident.md": "private incident",
        }
        for relative, content in private_files.items():
            path = package / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

        self.git(repository, "add", "CourseAxis")
        self.git(repository, "commit", "--quiet", "-m", "fixture")
        return repository, package

    def tree_fingerprint(self, root: Path) -> list[tuple[str, int, str]]:
        entries: list[tuple[str, int, str]] = []
        for path in sorted(item for item in root.rglob("*") if item.is_file()):
            relative = path.relative_to(root).as_posix()
            mode = path.stat().st_mode & 0o777
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            entries.append((relative, mode, digest))
        return entries

    def test_exports_only_committed_allowlisted_files_and_preserves_mode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            (package / "README.md").write_text("uncommitted private change\n", encoding="utf-8")
            (package / "android").mkdir(exist_ok=True)
            (package / "android/untracked.txt").write_text("untracked", encoding="utf-8")
            destination = root / "candidate"

            result = export_public_candidate.export_candidate(
                git_root=repository,
                package_root=package,
                destination=destination,
            )

            self.assertEqual(
                (destination / "README.md").read_text(encoding="utf-8"),
                "committed README.md\n",
            )
            self.assertFalse((destination / "android/untracked.txt").exists())
            self.assertFalse((destination / "docs/superpowers").exists())
            self.assertFalse((destination / "knowledge/tasks").exists())
            self.assertFalse((destination / "MACOS_BUILD_REPORT.md").exists())
            self.assertEqual((destination / "scripts/tool.sh").stat().st_mode & 0o777, 0o755)
            self.assertEqual(result.source_commit, self.git(repository, "rev-parse", "HEAD"))
            self.assertIn("scripts/tool.sh", result.paths)

    def test_rejects_committed_private_site_configuration(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            for relative in (
                "site/.env.production",
                "site/private-config.json",
                "site/private-config.example",
            ):
                path = package / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("private", encoding="utf-8")
            self.git(repository, "add", "-f", "CourseAxis/site/.env.production")
            self.git(repository, "add", "-f", "CourseAxis/site/private-config.json")
            self.git(repository, "add", "-f", "CourseAxis/site/private-config.example")
            self.git(repository, "commit", "--quiet", "-m", "private site config")

            with self.assertRaises(export_public_candidate.ForbiddenPublicPathError):
                export_public_candidate.export_candidate(
                    git_root=repository,
                    package_root=package,
                    destination=root / "candidate",
                )

    def test_rejects_committed_generated_site_directories(self) -> None:
        for relative in (
            "site/.npm-cache/content/private.txt",
            "site/node_modules/package/source.js",
            "site/dist/assets/index.js",
            "site/coverage/report.html",
        ):
            with self.subTest(relative=relative), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                repository, package = self.create_repository(root)
                path = package / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("private", encoding="utf-8")
                self.git(repository, "add", "-f", f"CourseAxis/{relative}")
                self.git(repository, "commit", "--quiet", "-m", "generated site file")

                with self.assertRaises(export_public_candidate.ForbiddenPublicPathError):
                    export_public_candidate.export_candidate(
                        git_root=repository,
                        package_root=package,
                        destination=root / "candidate",
                    )

    def test_refuses_existing_destination_without_deleting_marker(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            destination = root / "candidate"
            destination.mkdir()
            marker = destination / "preserve.txt"
            marker.write_text("preserve", encoding="utf-8")

            with self.assertRaises(FileExistsError):
                export_public_candidate.export_candidate(
                    git_root=repository,
                    package_root=package,
                    destination=destination,
                )

            self.assertEqual(marker.read_text(encoding="utf-8"), "preserve")

    def test_rejects_allowlisted_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            os.symlink("README.md", package / "docs/link.md")
            self.git(repository, "add", "CourseAxis/docs/link.md")
            self.git(repository, "commit", "--quiet", "-m", "symlink")

            with self.assertRaises(export_public_candidate.UnsupportedGitEntryError):
                export_public_candidate.export_candidate(
                    git_root=repository,
                    package_root=package,
                    destination=root / "candidate",
                )

    def test_missing_required_public_contract_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            (package / "SECURITY.md").unlink()
            self.git(repository, "add", "CourseAxis/SECURITY.md")
            self.git(repository, "commit", "--quiet", "-m", "remove security")

            with self.assertRaises(export_public_candidate.MissingPublicContractError):
                export_public_candidate.export_candidate(
                    git_root=repository,
                    package_root=package,
                    destination=root / "candidate",
                )

    def test_same_commit_exports_identical_files_and_modes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository, package = self.create_repository(root)
            first = root / "first"
            second = root / "second"

            export_public_candidate.export_candidate(repository, package, first)
            export_public_candidate.export_candidate(repository, package, second)

            self.assertEqual(self.tree_fingerprint(first), self.tree_fingerprint(second))


if __name__ == "__main__":
    unittest.main()
