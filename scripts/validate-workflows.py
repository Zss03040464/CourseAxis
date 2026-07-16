#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_FILES = (
    ".github/workflows/ci.yml",
    ".github/workflows/codeql.yml",
    ".github/workflows/dependency-review.yml",
)
PINNED_ACTIONS = {
    "actions/checkout": "9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0",
    "actions/setup-java": "0f481fcb613427c0f801b606911222b5b6f3083a",
    "actions/setup-python": "ece7cb06caefa5fff74198d8649806c4678c61a1",
    "actions/setup-go": "924ae3a1cded613372ab5595356fb5720e22ba16",
    "actions/setup-node": "249970729cb0ef3589644e2896645e5dc5ba9c38",
    "actions/dependency-review-action": "a1d282b36b6f3519aa1f3fc636f609c47dddb294",
    "github/codeql-action/init": "99df26d4f13ea111d4ec1a7dddef6063f76b97e9",
    "github/codeql-action/analyze": "99df26d4f13ea111d4ec1a7dddef6063f76b97e9",
}
USES_PATTERN = re.compile(r"^\s*uses:\s*([^\s#]+)", re.MULTILINE)
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def workflow_permissions(text: str) -> set[str]:
    match = re.search(r"^permissions:\n((?:^  [^\n]+\n?)*)", text, re.MULTILINE)
    if match is None:
        return set()
    return {
        line.strip()
        for line in match.group(1).splitlines()
        if line.strip()
    }


def validate_action_references(relative: str, text: str) -> list[str]:
    errors: list[str] = []
    references = USES_PATTERN.findall(text)
    for reference in references:
        if reference.startswith("./"):
            continue
        if "@" not in reference:
            errors.append(f"{relative}: action reference has no revision: {reference}")
            continue
        action, revision = reference.rsplit("@", 1)
        if not COMMIT_PATTERN.fullmatch(revision):
            errors.append(
                f"{relative}: action must use a 40-character commit SHA: {reference}"
            )
            continue
        expected = PINNED_ACTIONS.get(action)
        if expected is None:
            errors.append(f"{relative}: unapproved external action: {action}")
        elif revision != expected:
            errors.append(f"{relative}: unexpected pinned revision for {action}")
    checkout_count = sum(reference.startswith("actions/checkout@") for reference in references)
    if text.count("persist-credentials: false") != checkout_count:
        errors.append(f"{relative}: every checkout must disable persisted credentials")
    return errors


def validate_workflow(relative: str, text: str) -> list[str]:
    errors = validate_action_references(relative, text)
    if "pull_request_target:" in text:
        errors.append(f"{relative}: pull_request_target is forbidden")
    if "secrets." in text:
        errors.append(f"{relative}: pull request workflow contains a secret reference")
    if any(
        forbidden in text
        for forbidden in ("write-all", "contents: write", "id-token: write")
    ):
        errors.append(f"{relative}: workflow requests an unapproved write permission")
    if "concurrency:" not in text or "cancel-in-progress: true" not in text:
        errors.append(f"{relative}: workflow must cancel superseded runs")

    permissions = workflow_permissions(text)
    expected_permissions = {"contents: read"}
    if relative.endswith("codeql.yml"):
        expected_permissions.add("security-events: write")
    if permissions != expected_permissions:
        errors.append(
            f"{relative}: top-level permissions are not the approved minimum"
        )

    if relative.endswith("ci.yml"):
        for fragment in (
            "runs-on: ubuntu-24.04",
            "runs-on: macos-26",
            'java-version: "17"',
            'sdkmanager "platforms;android-36" "build-tools;36.0.0"',
            "--dependency-verification=strict",
            "scripts/install-xcodegen.sh",
            "actionlint@914e7df21a07ef503a81201c76d2b11c789d3fca",
            "site/.node-version",
            "npm ci --ignore-scripts",
            "npm audit --audit-level=high",
            "test -s dist/LICENSE.txt",
            "test -s dist/THIRD_PARTY_NOTICES.txt",
            "python3 scripts/validate-site-build.py",
        ):
            if fragment not in text:
                errors.append(f"{relative}: missing required CI contract: {fragment}")
        for job in ("hygiene", "website", "server", "android", "macos"):
            if not re.search(rf"^  {job}:$", text, re.MULTILINE):
                errors.append(f"{relative}: missing required job: {job}")
    elif relative.endswith("codeql.yml"):
        for fragment in (
            "language: java-kotlin",
            "language: swift",
            "build-mode: manual",
            "runs-on: ${{ matrix.os }}",
            "os: macos-26",
            "scripts/install-xcodegen.sh",
        ):
            if fragment not in text:
                errors.append(f"{relative}: missing CodeQL contract: {fragment}")
        if text.find("scripts/install-xcodegen.sh") > text.find(
            "github/codeql-action/init@"
        ):
            errors.append(
                f"{relative}: XcodeGen must be built before CodeQL tracing starts"
            )
    elif relative.endswith("dependency-review.yml"):
        if not re.search(r"^on:\n  pull_request:\n", text, re.MULTILINE):
            errors.append(f"{relative}: dependency review must run on pull_request only")
        on_block = re.search(r"^on:\n((?:^  [^\n]+\n?)*)", text, re.MULTILINE)
        if on_block is None or "push:" in on_block.group(1):
            errors.append(f"{relative}: dependency review must run on pull_request only")
    return errors


def validate_dependabot(root: Path) -> list[str]:
    relative = ".github/dependabot.yml"
    path = root / relative
    if not path.is_file():
        return [f"missing required configuration: {relative}"]
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    expected = {
        "github-actions": "/",
        "gradle": "/android",
        "npm": "/site",
        "gomod": "/server",
        "docker": "/server",
    }
    blocks: dict[str, str] = {}
    for block in re.split(r"(?=^  - package-ecosystem:)", text, flags=re.MULTILINE):
        match = re.search(r'^  - package-ecosystem: "([^"]+)"', block, re.MULTILINE)
        if match is None:
            continue
        ecosystem = match.group(1)
        if ecosystem in blocks:
            errors.append(f"{relative}: duplicate ecosystem entry: {ecosystem}")
        blocks[ecosystem] = block

    for ecosystem, directory in expected.items():
        block = blocks.get(ecosystem, "")
        if (
            f'directory: "{directory}"' not in block
            or 'interval: "weekly"' not in block
            or "open-pull-requests-limit: 5" not in block
        ):
            errors.append(f"{relative}: missing weekly {ecosystem} updates for {directory}")
    unexpected = sorted(set(blocks) - set(expected))
    for ecosystem in unexpected:
        errors.append(f"{relative}: unapproved ecosystem entry: {ecosystem}")
    return errors


def validate_tree(root: Path = ROOT) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    for relative in WORKFLOW_FILES:
        path = root / relative
        if not path.is_file():
            errors.append(f"missing required workflow: {relative}")
            continue
        errors.extend(validate_workflow(relative, path.read_text(encoding="utf-8")))
    errors.extend(validate_dependabot(root))
    release = root / ".github/release.yml"
    if not release.is_file() or "skip-changelog" not in release.read_text(encoding="utf-8"):
        errors.append(".github/release.yml: release category contract is missing")
    installer = root / "scripts/install-xcodegen.sh"
    if not installer.is_file() or "8d3d3476a69ae3e5d68e1adccc701c410c05eb36" not in installer.read_text(encoding="utf-8"):
        errors.append("scripts/install-xcodegen.sh: XcodeGen commit is not pinned")
    return sorted(set(errors))


def main() -> int:
    errors = validate_tree()
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("GitHub workflow policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
