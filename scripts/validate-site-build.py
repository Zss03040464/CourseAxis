#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


REQUIRED_FILES = (
    "index.html",
    "LICENSE.txt",
    "THIRD_PARTY_NOTICES.txt",
    "courseaxis-app-icon.png",
    "courseaxis-starline-background.png",
)
CSS_URL = re.compile(r"url\(\s*(['\"]?)([^)'\"]+)\1\s*\)", re.IGNORECASE)


class AssetReferenceParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.references: list[str] = []

    def handle_starttag(
        self, tag: str, attributes: list[tuple[str, str | None]]
    ) -> None:
        del tag
        for name, value in attributes:
            if name in {"href", "src"} and value:
                self.references.append(value)


def validate_reference(
    reference: str, owner: Path, dist: Path, errors: list[str]
) -> None:
    if reference.startswith("/"):
        errors.append(f"root-absolute asset reference in {owner.name}: {reference}")
        return
    parsed = urlsplit(reference)
    if parsed.scheme or parsed.netloc or not parsed.path:
        return
    relative = unquote(parsed.path)
    candidate = (owner.parent / relative).resolve()
    try:
        candidate.relative_to(dist)
    except ValueError:
        errors.append(f"asset reference escapes dist in {owner.name}: {reference}")
        return
    if not candidate.is_file():
        errors.append(f"missing referenced asset in {owner.name}: {reference}")


def validate_site_build(dist: Path) -> list[str]:
    dist = Path(dist).resolve()
    errors: list[str] = []
    if not dist.is_dir():
        return [f"site build directory does not exist: {dist}"]

    for relative in REQUIRED_FILES:
        if not (dist / relative).is_file():
            errors.append(f"required site build file is missing: {relative}")

    index = dist / "index.html"
    if index.is_file():
        parser = AssetReferenceParser()
        parser.feed(index.read_text(encoding="utf-8"))
        for reference in parser.references:
            validate_reference(reference, index, dist, errors)

    for stylesheet in sorted(dist.rglob("*.css")):
        source = stylesheet.read_text(encoding="utf-8")
        for match in CSS_URL.finditer(source):
            validate_reference(match.group(2), stylesheet, dist, errors)

    javascript = "\n".join(
        path.read_text(encoding="utf-8") for path in sorted(dist.rglob("*.js"))
    )
    if javascript and "./courseaxis-app-icon.png" not in javascript:
        errors.append("built JavaScript is missing the relative CourseAxis icon reference")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate a portable CourseAxis static website build."
    )
    parser.add_argument("--dist", type=Path, default=Path("site/dist"))
    arguments = parser.parse_args()
    errors = validate_site_build(arguments.dist)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Static website build is portable: {arguments.dist}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
