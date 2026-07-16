#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ALWAYS_IGNORED_DIRECTORY_NAMES = {".git"}
GENERATED_DIRECTORY_NAMES = {
    ".gradle",
    ".idea",
    ".npm-cache",
    ".build",
    "__pycache__",
    "DerivedData",
    "artifacts",
    "build",
    "coverage",
    "dist",
    "node_modules",
    "server-data",
    "xcuserdata",
}
SOURCE_PRIVATE_PREFIXES = (
    "docs/superpowers/",
    "knowledge/",
)
SOURCE_PRIVATE_FILES = {
    "MACOS_BUILD_REPORT.md",
    "PACKAGE_MANIFEST.md",
    "PROJECT.md",
    "TASKS.md",
    "design-qa.md",
}
PUBLIC_FORBIDDEN_PREFIXES = (
    "docs/superpowers/",
    "knowledge/incidents/",
    "knowledge/tasks/",
)
PUBLIC_FORBIDDEN_FILES = {
    "MACOS_BUILD_REPORT.md",
    "PACKAGE_MANIFEST.md",
    "design-qa.md",
}
SENSITIVE_SUFFIXES = {
    ".jks",
    ".keystore",
    ".mobileprovision",
    ".p12",
    ".pfx",
    ".pkcs12",
}
SENSITIVE_FILE_NAMES = {
    ".env",
    "auth.json",
    "credentials.json",
    "google-services.json",
    "keystore.properties",
    "service-account.json",
}
TEXT_SUFFIXES = {
    "",
    ".gradle",
    ".html",
    ".java",
    ".js",
    ".json",
    ".kts",
    ".kt",
    ".md",
    ".plist",
    ".properties",
    ".py",
    ".sh",
    ".swift",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
SECRET_PATTERNS = (
    ("private key", re.compile(r"-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----")),
    ("GitHub token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b")),
    ("AWS access key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("Slack token", re.compile(r"\bxox(?:a|b|p|r|s)-[A-Za-z0-9-]{16,}\b")),
    (
        "Bearer token",
        re.compile(r"Authorization\s*:\s*Bearer\s+[A-Za-z0-9._~-]{12,}", re.IGNORECASE),
    ),
)
INTERNAL_TEXT_PATTERNS = (
    ("UI automation evidence", re.compile(r"\bComputer" + r"\s+Use\b")),
    (
        "real device identifier",
        re.compile(r"\b(?:CPH" + r"2747|One" + r"Plus)\b"),
    ),
    (
        "internal CourseAxis evidence path",
        re.compile(r"Library/Developer/" + r"CourseAxis/Evidence"),
    ),
    (
        "internal Codex process",
        re.compile(r"\bCodex\s+(?:agent|task|session|thread)\b", re.IGNORECASE),
    ),
)
PRIVATE_EMAIL_PATTERN = re.compile(
    r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.(?:ctc|local)\b",
    re.IGNORECASE,
)
POSIX_USER_PATH_PATTERN = re.compile(r'/(?:Users|home)/([^/\s"]+)')
WINDOWS_USER_PATH_PATTERN = re.compile(r'\b[A-Za-z]:\\Users\\[^\\\s"]+')
QR_IMAGE_PATTERN = re.compile(
    r"(?:alipay|wechat|weixin|qrcode|payment-qr).+\.(?:jpe?g|png|webp)$",
    re.IGNORECASE,
)


def generated_directory_root(path: Path) -> str | None:
    for index, part in enumerate(path.parts):
        if part in GENERATED_DIRECTORY_NAMES:
            return Path(*path.parts[: index + 1]).as_posix()
    return None


def source_private(relative: str) -> bool:
    return relative in SOURCE_PRIVATE_FILES or relative.startswith(SOURCE_PRIVATE_PREFIXES)


def sensitive_file_error(relative: str, path: Path) -> str | None:
    lower_name = path.name.lower()
    if path.suffix.lower() in SENSITIVE_SUFFIXES:
        return f"sensitive file is forbidden: {relative}"
    is_private_environment = (
        lower_name == ".env" or lower_name.startswith(".env.")
    ) and lower_name != ".env.example"
    is_private_site_config = lower_name.startswith("private-config.")
    if lower_name in SENSITIVE_FILE_NAMES or is_private_environment or is_private_site_config:
        return f"sensitive file is forbidden: {relative}"
    if relative.endswith("/schedule.document.json") or relative == "schedule.document.json":
        if relative != "samples/schedule.document.json":
            return f"exported schedule is forbidden: {relative}"
    if QR_IMAGE_PATTERN.search(lower_name):
        return f"payment QR image is forbidden: {relative}"
    return None


def text_errors(relative: str, text: str, mode: str) -> list[str]:
    errors: list[str] = []
    for label, pattern in SECRET_PATTERNS:
        if pattern.search(text):
            errors.append(f"{label} found in {relative}")
    for match in POSIX_USER_PATH_PATTERN.finditer(text):
        user = match.group(1)
        if not user.startswith(("<", "$")):
            errors.append(f"absolute user path found in {relative}")
            break
    if WINDOWS_USER_PATH_PATTERN.search(text):
        errors.append(f"absolute user path found in {relative}")
    if PRIVATE_EMAIL_PATTERN.search(text):
        errors.append(f"private email found in {relative}")
    if mode == "public":
        for label, pattern in INTERNAL_TEXT_PATTERNS:
            if pattern.search(text):
                errors.append(f"{label} found in {relative}")
    return errors


def sample_errors(root: Path) -> list[str]:
    samples = root / "samples"
    if not samples.exists():
        return []
    errors: list[str] = []
    readme = samples / "README.md"
    if not readme.is_file() or "完全合成" not in readme.read_text(encoding="utf-8"):
        errors.append("samples/README.md must declare all samples completely synthetic")
    allowed_json = {"schedule.document.json", "section_times.json"}
    actual_json = {path.name for path in samples.glob("*.json")}
    unexpected = sorted(actual_json - allowed_json)
    if unexpected:
        errors.append(f"unexpected legacy sample JSON: {', '.join(unexpected)}")
    schedule_path = samples / "schedule.document.json"
    if schedule_path.is_file():
        try:
            schedule = json.loads(schedule_path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as error:
            errors.append(f"invalid synthetic schedule sample: {error}")
        else:
            terms = schedule.get("terms", []) if isinstance(schedule, dict) else []
            events = schedule.get("events", []) if isinstance(schedule, dict) else []
            term_values_are_synthetic = bool(terms) and all(
                isinstance(term, dict)
                and term.get("school") == "示例大学"
                and term.get("campus") == "示例校区"
                for term in terms
            )
            event_values_are_synthetic = bool(events) and all(
                isinstance(event, dict)
                and str(event.get("title", "")).startswith("合成")
                and (
                    not event.get("teacher")
                    or str(event.get("teacher", "")).startswith("虚构")
                )
                for event in events
            )
            if (
                not isinstance(schedule, dict)
                or schedule.get("documentId") != "synthetic-courseaxis-demo"
                or not term_values_are_synthetic
                or not event_values_are_synthetic
            ):
                errors.append(
                    "synthetic schedule sample must use the reserved synthetic identity"
                )
    section_path = samples / "section_times.json"
    if section_path.is_file():
        try:
            sections = json.loads(section_path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as error:
            errors.append(f"invalid synthetic section sample: {error}")
        else:
            if not isinstance(sections, list):
                errors.append("synthetic section sample must be an array")
    return errors


def validate_tree(root: Path = ROOT, mode: str = "source") -> list[str]:
    root = root.resolve()
    if mode not in {"source", "public"}:
        raise ValueError(f"unsupported hygiene mode: {mode}")
    errors: list[str] = []
    for path in sorted(root.rglob("*")):
        relative_object = path.relative_to(root)
        if any(
            part in ALWAYS_IGNORED_DIRECTORY_NAMES
            for part in relative_object.parts
        ):
            continue
        relative = relative_object.as_posix()
        generated_root = generated_directory_root(relative_object)
        if generated_root is not None:
            if mode == "public" and path.is_dir() and relative == generated_root:
                errors.append(
                    f"generated directory is forbidden: {generated_root}"
                )
            continue
        if mode == "source" and source_private(relative):
            continue
        if mode == "public":
            if relative in PUBLIC_FORBIDDEN_FILES or relative.startswith(
                PUBLIC_FORBIDDEN_PREFIXES
            ):
                errors.append(f"internal process path is forbidden: {relative}")
                if path.is_dir():
                    continue
        if path.is_dir():
            continue
        if path.is_symlink():
            errors.append(f"symlink is forbidden in public source: {relative}")
            continue
        file_error = sensitive_file_error(relative, path)
        if file_error is not None:
            errors.append(file_error)
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            continue
        errors.extend(text_errors(relative, text, mode))
    errors.extend(sample_errors(root))
    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate CourseAxis public-tree hygiene")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--mode", choices=("source", "public"), default="source")
    arguments = parser.parse_args()
    errors = validate_tree(arguments.root, arguments.mode)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Public hygiene ({arguments.mode}): OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
