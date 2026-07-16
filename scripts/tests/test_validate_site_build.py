import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/validate-site-build.py"
SPEC = importlib.util.spec_from_file_location("validate_site_build", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
validate_site_build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validate_site_build)


class ValidateSiteBuildTests(unittest.TestCase):
    def make_dist(self, root: Path) -> Path:
        dist = root / "dist"
        assets = dist / "assets"
        assets.mkdir(parents=True)
        (dist / "index.html").write_text(
            '<link rel="stylesheet" href="./assets/app.css">'
            '<script type="module" src="./assets/app.js"></script>',
            encoding="utf-8",
        )
        (assets / "app.css").write_text(
            'body { background: url("../courseaxis-starline-background.png"); }',
            encoding="utf-8",
        )
        (assets / "app.js").write_text(
            'const icon = "./courseaxis-app-icon.png";',
            encoding="utf-8",
        )
        for name in (
            "LICENSE.txt",
            "THIRD_PARTY_NOTICES.txt",
            "courseaxis-app-icon.png",
            "courseaxis-starline-background.png",
        ):
            (dist / name).write_bytes(b"content")
        return dist

    def test_accepts_complete_relative_static_build(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            dist = self.make_dist(Path(temporary))
            self.assertEqual(validate_site_build.validate_site_build(dist), [])

    def test_rejects_root_absolute_asset_reference(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            dist = self.make_dist(Path(temporary))
            (dist / "index.html").write_text(
                '<script type="module" src="/assets/app.js"></script>',
                encoding="utf-8",
            )
            errors = validate_site_build.validate_site_build(dist)
            self.assertTrue(any("root-absolute" in error for error in errors))

    def test_rejects_missing_runtime_license(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            dist = self.make_dist(Path(temporary))
            (dist / "THIRD_PARTY_NOTICES.txt").unlink()
            errors = validate_site_build.validate_site_build(dist)
            self.assertTrue(
                any("THIRD_PARTY_NOTICES.txt" in error for error in errors)
            )

    def test_rejects_missing_relative_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            dist = self.make_dist(Path(temporary))
            (dist / "assets/app.css").unlink()
            errors = validate_site_build.validate_site_build(dist)
            self.assertTrue(any("missing referenced asset" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
