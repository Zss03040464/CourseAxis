#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import NamedTuple


PUBLIC_ROOT_FILES = frozenset(
    {
        ".gitignore",
        "AGENTS.md",
        "CHANGELOG.md",
        "CODE_OF_CONDUCT.md",
        "CONTRIBUTING.md",
        "LICENSE",
        "PRIVACY.md",
        "PROJECT.md",
        "README.md",
        "SECURITY.md",
        "SUPPORT.md",
        "TASKS.md",
        "THIRD_PARTY_NOTICES.md",
        "THREAT_MODEL.md",
    }
)
PUBLIC_DIRECTORY_PREFIXES = (
    ".github/",
    "android/",
    "docs/",
    "licenses/",
    "macos/",
    "providers/",
    "reference-core/",
    "samples/",
    "schema/",
    "scripts/",
    "server/",
    "site/",
)
PUBLIC_EXACT_PATHS = frozenset({"knowledge/HOME.md"})
PRIVATE_PREFIXES = (
    "docs/superpowers/",
    "knowledge/incidents/",
    "knowledge/tasks/",
)
PRIVATE_FILES = frozenset(
    {
        "MACOS_BUILD_REPORT.md",
        "PACKAGE_MANIFEST.md",
        "design-qa.md",
    }
)
REQUIRED_PUBLIC_PATHS = frozenset(
    {
        ".gitignore",
        ".github/dependabot.yml",
        ".github/workflows/ci.yml",
        ".github/workflows/codeql.yml",
        ".github/workflows/dependency-review.yml",
        "AGENTS.md",
        "CHANGELOG.md",
        "CODE_OF_CONDUCT.md",
        "CONTRIBUTING.md",
        "LICENSE",
        "PRIVACY.md",
        "PROJECT.md",
        "README.md",
        "SECURITY.md",
        "SUPPORT.md",
        "TASKS.md",
        "THIRD_PARTY_NOTICES.md",
        "THREAT_MODEL.md",
        "docs/ACCEPTANCE.md",
        "docs/ARCHITECTURE.md",
        "docs/BUILD_AND_SIGN.md",
        "docs/IMPORT_PROTOCOL.md",
        "docs/PROVIDER_DEVELOPMENT.md",
        "docs/RELEASE.md",
        "docs/SYNC_DEPLOYMENT.md",
        "knowledge/HOME.md",
        "providers/catalog.json",
        "schema/schedule.schema.json",
        "scripts/check-public-hygiene.py",
        "scripts/export-public-candidate.py",
        "scripts/validate-site-build.py",
        "scripts/validate.sh",
        "site/README.md",
        "site/.env.example",
        "site/.node-version",
        "site/index.html",
        "site/package-lock.json",
        "site/package.json",
        "site/public/LICENSE.txt",
        "site/public/THIRD_PARTY_NOTICES.txt",
        "site/public/courseaxis-app-icon.png",
        "site/public/courseaxis-starline-background.png",
        "site/src/App.jsx",
        "site/src/main.jsx",
        "site/src/motion.js",
        "site/src/motion.test.js",
        "site/src/siteConfig.js",
        "site/src/siteConfig.test.js",
        "site/src/styles.css",
        "site/vite.config.mjs",
    }
)
SUPPORTED_BLOB_MODES = {"100644": 0o644, "100755": 0o755}
FORBIDDEN_SITE_GENERATED_PARTS = frozenset(
    {".npm-cache", "coverage", "dist", "node_modules"}
)


class PublicCandidateExportError(RuntimeError):
    """Base class for failures that stop a public candidate export."""


class InvalidExportLayoutError(PublicCandidateExportError):
    """Raised when repository or package paths cannot form a safe export root."""


class UnsupportedGitEntryError(PublicCandidateExportError):
    """Raised when an allowlisted path is not a regular Git blob."""


class MissingPublicContractError(PublicCandidateExportError):
    """Raised when required public files are absent from the selected commit."""


class ForbiddenPublicPathError(PublicCandidateExportError):
    """Raised when a committed site path must never enter a public candidate."""


class GitEntry(NamedTuple):
    mode: str
    object_type: str
    object_id: str
    relative_path: str


class ExportResult(NamedTuple):
    source_commit: str
    paths: tuple[str, ...]


def run_git(git_root: Path, arguments: list[str]) -> bytes:
    try:
        result = subprocess.run(
            ["git", "-C", os.fspath(git_root), *arguments],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
    except subprocess.CalledProcessError as error:
        detail = error.stderr.decode("utf-8", errors="replace").strip()
        raise PublicCandidateExportError(
            f"git {' '.join(arguments)} failed: {detail}"
        ) from error
    return result.stdout


def safe_relative_path(value: str) -> str:
    path = PurePosixPath(value)
    if not value or path.is_absolute() or ".." in path.parts or "." in path.parts:
        raise InvalidExportLayoutError(f"unsafe Git path: {value!r}")
    normalized = path.as_posix()
    if normalized != value or "\\" in value:
        raise InvalidExportLayoutError(f"non-canonical Git path: {value!r}")
    return normalized


def is_public_path(relative: str) -> bool:
    if relative in PRIVATE_FILES or relative.startswith(PRIVATE_PREFIXES):
        return False
    return (
        relative in PUBLIC_ROOT_FILES
        or relative in PUBLIC_EXACT_PATHS
        or relative.startswith(PUBLIC_DIRECTORY_PREFIXES)
    )


def forbidden_site_path(relative: str) -> bool:
    path = PurePosixPath(relative)
    if not path.parts or path.parts[0] != "site":
        return False
    lower_name = path.name.lower()
    private_environment = (
        lower_name == ".env" or lower_name.startswith(".env.")
    ) and lower_name != ".env.example"
    private_config = lower_name.startswith("private-config.")
    generated = any(
        part in FORBIDDEN_SITE_GENERATED_PARTS for part in path.parts[1:]
    )
    return private_environment or private_config or generated


def package_prefix(git_root: Path, package_root: Path) -> str:
    resolved_git_root = git_root.resolve(strict=True)
    resolved_package_root = package_root.resolve(strict=True)
    try:
        relative = resolved_package_root.relative_to(resolved_git_root)
    except ValueError as error:
        raise InvalidExportLayoutError(
            "package root must be inside the Git repository"
        ) from error
    prefix = relative.as_posix()
    return "" if prefix == "." else safe_relative_path(prefix)


def parse_tree(raw_tree: bytes, prefix: str) -> list[GitEntry]:
    entries: list[GitEntry] = []
    expected_prefix = f"{prefix}/" if prefix else ""
    for record in raw_tree.split(b"\0"):
        if not record:
            continue
        try:
            metadata, raw_path = record.split(b"\t", 1)
            mode, object_type, object_id = metadata.decode("ascii").split(" ", 2)
            tree_path = raw_path.decode("utf-8")
        except (UnicodeDecodeError, ValueError) as error:
            raise PublicCandidateExportError("unparseable git ls-tree record") from error
        if expected_prefix:
            if not tree_path.startswith(expected_prefix):
                raise InvalidExportLayoutError(
                    f"Git path escaped package root: {tree_path!r}"
                )
            relative = tree_path[len(expected_prefix) :]
        else:
            relative = tree_path
        relative = safe_relative_path(relative)
        if forbidden_site_path(relative):
            raise ForbiddenPublicPathError(
                f"forbidden committed site path: {relative}"
            )
        if is_public_path(relative):
            entries.append(GitEntry(mode, object_type, object_id, relative))
    return sorted(entries, key=lambda entry: entry.relative_path)


def validate_entries(entries: list[GitEntry]) -> None:
    selected_paths: set[str] = set()
    for entry in entries:
        if entry.relative_path in selected_paths:
            raise InvalidExportLayoutError(
                f"duplicate public path: {entry.relative_path}"
            )
        selected_paths.add(entry.relative_path)
        if entry.object_type != "blob" or entry.mode not in SUPPORTED_BLOB_MODES:
            raise UnsupportedGitEntryError(
                "unsupported public Git entry "
                f"{entry.relative_path}: mode={entry.mode} type={entry.object_type}"
            )
    missing = sorted(REQUIRED_PUBLIC_PATHS - selected_paths)
    if missing:
        raise MissingPublicContractError(
            "required public contracts are missing: " + ", ".join(missing)
        )


def export_candidate(
    git_root: Path,
    package_root: Path,
    destination: Path,
    revision: str = "HEAD",
) -> ExportResult:
    git_root = Path(git_root)
    package_root = Path(package_root)
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise FileExistsError(f"destination already exists: {destination}")

    prefix = package_prefix(git_root, package_root)
    source_commit = run_git(
        git_root, ["rev-parse", "--verify", f"{revision}^{{commit}}"]
    ).decode("ascii").strip()
    tree_arguments = ["ls-tree", "-r", "-z", "--full-tree", source_commit]
    if prefix:
        tree_arguments.extend(["--", prefix])
    entries = parse_tree(run_git(git_root, tree_arguments), prefix)
    validate_entries(entries)

    destination.mkdir(parents=True)
    for entry in entries:
        output = destination / entry.relative_path
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(run_git(git_root, ["cat-file", "blob", entry.object_id]))
        output.chmod(SUPPORTED_BLOB_MODES[entry.mode])
    return ExportResult(source_commit, tuple(entry.relative_path for entry in entries))


def discover_roots(script_path: Path) -> tuple[Path, Path]:
    package_root = script_path.resolve().parents[1]
    raw_git_root = run_git(package_root, ["rev-parse", "--show-toplevel"])
    return Path(raw_git_root.decode("utf-8").strip()), package_root


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export a deterministic CourseAxis public tree from committed Git blobs"
    )
    parser.add_argument("destination", type=Path)
    parser.add_argument("--revision", default="HEAD")
    arguments = parser.parse_args()
    try:
        git_root, package_root = discover_roots(Path(__file__))
        result = export_candidate(
            git_root=git_root,
            package_root=package_root,
            destination=arguments.destination,
            revision=arguments.revision,
        )
    except (OSError, PublicCandidateExportError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Exported {len(result.paths)} files from commit {result.source_commit}")
    print(f"Destination: {arguments.destination.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
