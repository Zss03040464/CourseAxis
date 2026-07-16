#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit
from uuid import UUID


ROOT = Path(__file__).resolve().parents[1]
ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
VENDOR_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
PROVIDER_KEYS = {
    "schemaVersion",
    "id",
    "schoolName",
    "vendor",
    "adapterId",
    "sessionNamespace",
    "allowedOrigin",
    "pathPrefix",
    "loginPath",
    "homePath",
    "requiresThirdPartyCookies",
    "semesterCodes",
}


def load_json(path: Path, errors: list[str]) -> object | None:
    if not path.is_file():
        errors.append(f"missing JSON file: {path.relative_to(ROOT)}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        errors.append(f"invalid JSON {path.relative_to(ROOT)}: {error}")
        return None


def valid_origin(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        parsed = urlsplit(value)
        port = parsed.port
    except ValueError:
        return False
    return (
        parsed.scheme == "https"
        and bool(parsed.hostname)
        and parsed.username is None
        and parsed.password is None
        and port in (None, 443)
        and parsed.path in ("", "/")
        and not parsed.query
        and not parsed.fragment
    )


def valid_path(value: object, *, prefix: bool) -> bool:
    if not isinstance(value, str) or not value.startswith("/"):
        return False
    if prefix and not value.endswith("/"):
        return False
    decoded = unquote(value)
    parts = decoded.split("/")
    return "\\" not in decoded and "\0" not in decoded and not any(part in {".", ".."} for part in parts)


def validate_provider(expected_id: str, value: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(value, dict):
        return [f"provider {expected_id} must be an object"]
    missing = sorted(PROVIDER_KEYS - value.keys())
    extra = sorted(value.keys() - PROVIDER_KEYS)
    if missing:
        errors.append(f"provider {expected_id} missing keys: {', '.join(missing)}")
    if extra:
        errors.append(f"provider {expected_id} has unknown keys: {', '.join(extra)}")
    if value.get("schemaVersion") != 1:
        errors.append(f"provider {expected_id} schemaVersion must be 1")
    if value.get("id") != expected_id or not ID_PATTERN.fullmatch(expected_id):
        errors.append(f"provider id mismatch or invalid: {expected_id}")
    if not isinstance(value.get("schoolName"), str) or not value["schoolName"].strip():
        errors.append(f"provider {expected_id} schoolName is empty")
    if not isinstance(value.get("vendor"), str) or not VENDOR_PATTERN.fullmatch(value["vendor"]):
        errors.append(f"provider {expected_id} vendor is invalid")
    adapter_id = value.get("adapterId")
    if not isinstance(adapter_id, str) or not ID_PATTERN.fullmatch(adapter_id):
        errors.append(f"provider {expected_id} adapterId is invalid")
    session_namespace = value.get("sessionNamespace")
    try:
        canonical_namespace = str(UUID(str(session_namespace)))
    except (ValueError, AttributeError):
        errors.append(f"provider {expected_id} sessionNamespace is not a UUID")
    else:
        if not isinstance(session_namespace, str) or canonical_namespace != session_namespace.lower():
            errors.append(f"provider {expected_id} sessionNamespace is not canonical")
    if not valid_origin(value.get("allowedOrigin")):
        errors.append(f"provider {expected_id} allowedOrigin must be an exact HTTPS origin")
    prefix = value.get("pathPrefix")
    if not valid_path(prefix, prefix=True):
        errors.append(f"provider {expected_id} pathPrefix is invalid")
    for key in ("loginPath", "homePath"):
        path = value.get(key)
        if not valid_path(path, prefix=False) or not isinstance(prefix, str) or not str(path).startswith(prefix):
            errors.append(f"provider {expected_id} {key} must stay inside pathPrefix")
    if not isinstance(value.get("requiresThirdPartyCookies"), bool):
        errors.append(f"provider {expected_id} requiresThirdPartyCookies must be boolean")
    semester_codes = value.get("semesterCodes")
    if not isinstance(semester_codes, dict) or not semester_codes or not all(
        isinstance(key, str)
        and key in {"first", "second", "third"}
        and isinstance(code, str)
        and bool(code.strip())
        for key, code in semester_codes.items()
    ) or len(set(semester_codes.values())) != len(semester_codes):
        errors.append(f"provider {expected_id} semesterCodes is invalid")
    return errors


def validate_fixture(path: Path, provider_ids: set[str]) -> list[str]:
    errors: list[str] = []
    value = load_json(path, errors)
    if not isinstance(value, dict):
        return errors or [f"fixture must be an object: {path.relative_to(ROOT)}"]
    if set(value) != {"synthetic", "providerId", "payload"}:
        errors.append(f"fixture keys must be synthetic/providerId/payload: {path.relative_to(ROOT)}")
    if value.get("synthetic") is not True:
        errors.append(f"fixture must declare synthetic=true: {path.relative_to(ROOT)}")
    if value.get("providerId") not in provider_ids:
        errors.append(f"fixture providerId is not in catalog: {path.relative_to(ROOT)}")
    if not isinstance(value.get("payload"), dict):
        errors.append(f"fixture payload must be an object: {path.relative_to(ROOT)}")
    serialized = json.dumps(value, ensure_ascii=False)
    if re.search(r"(?<!\d)\d{10,}(?!\d)", serialized):
        errors.append(f"fixture contains a long numeric identifier: {path.relative_to(ROOT)}")
    return errors


def validate_provider_catalog(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    for schema in (
        root / "schema/academic-provider.schema.json",
        root / "schema/academic-provider-catalog.schema.json",
    ):
        load_json(schema, errors)

    catalog_path = root / "providers/catalog.json"
    catalog = load_json(catalog_path, errors)
    if not isinstance(catalog, dict):
        return errors
    if set(catalog) != {"schemaVersion", "providers"}:
        errors.append("provider catalog keys must be schemaVersion/providers")
    if catalog.get("schemaVersion") != 1:
        errors.append("provider catalog schemaVersion must be 1")
    provider_ids = catalog.get("providers")
    if not isinstance(provider_ids, list) or not provider_ids:
        errors.append("provider catalog providers must be a non-empty array")
        return errors
    if len(provider_ids) != len(set(provider_ids)):
        errors.append("provider catalog contains duplicate provider IDs")
    if not all(isinstance(value, str) and ID_PATTERN.fullmatch(value) for value in provider_ids):
        errors.append("provider catalog contains an invalid provider ID")
        return errors

    known_ids = set(provider_ids)
    session_namespaces: dict[str, str] = {}
    for provider_id in provider_ids:
        provider_path = root / f"providers/{provider_id}/provider.json"
        provider = load_json(provider_path, errors)
        errors.extend(validate_provider(provider_id, provider))
        if isinstance(provider, dict) and isinstance(provider.get("sessionNamespace"), str):
            namespace = provider["sessionNamespace"].lower()
            previous = session_namespaces.get(namespace)
            if previous is not None:
                errors.append(
                    f"providers {previous} and {provider_id} share sessionNamespace"
                )
            else:
                session_namespaces[namespace] = provider_id
        if isinstance(provider, dict) and isinstance(provider.get("adapterId"), str):
            adapter = root / f"providers/adapters/{provider['adapterId']}/import.js"
            if not adapter.is_file():
                errors.append(f"missing adapter script: {adapter.relative_to(root)}")

    for name in ("courses.json", "exams.json"):
        errors.extend(validate_fixture(root / f"providers/fixtures/zhengfang-v1/{name}", known_ids))
    return errors


def main() -> int:
    errors = validate_provider_catalog()
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Provider catalog and synthetic fixtures: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
