import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "validate-providers.py"
SPEC = importlib.util.spec_from_file_location("validate_providers", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
validate_providers = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validate_providers)


def valid_provider() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "id": "synthetic-school",
        "schoolName": "合成学校",
        "vendor": "SYNTHETIC",
        "adapterId": "synthetic-v1",
        "sessionNamespace": "12345678-1234-4234-8234-123456789abc",
        "allowedOrigin": "https://jw.example.edu.cn",
        "pathPrefix": "/academic/",
        "loginPath": "/academic/login",
        "homePath": "/academic/home",
        "requiresThirdPartyCookies": False,
        "semesterCodes": {"first": "A", "second": "B"},
    }


class ProviderValidationTests(unittest.TestCase):
    def test_valid_provider_contract(self) -> None:
        self.assertEqual(
            validate_providers.validate_provider("synthetic-school", valid_provider()),
            [],
        )

    def test_noncanonical_session_namespace_is_rejected(self) -> None:
        provider = valid_provider()
        provider["sessionNamespace"] = "{12345678-1234-4234-8234-123456789ABC}"

        errors = validate_providers.validate_provider("synthetic-school", provider)

        self.assertTrue(any("not canonical" in error for error in errors), errors)

    def test_blank_semester_code_is_rejected(self) -> None:
        provider = valid_provider()
        provider["semesterCodes"] = {"first": " "}

        errors = validate_providers.validate_provider("synthetic-school", provider)

        self.assertTrue(any("semesterCodes is invalid" in error for error in errors), errors)

    def test_unsafe_path_is_rejected(self) -> None:
        provider = valid_provider()
        provider["pathPrefix"] = "/academic/../"

        errors = validate_providers.validate_provider("synthetic-school", provider)

        self.assertTrue(any("pathPrefix is invalid" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
