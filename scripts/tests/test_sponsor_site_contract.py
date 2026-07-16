import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EXPORT_SCRIPT = ROOT / "scripts/export-public-candidate.py"
SPEC = importlib.util.spec_from_file_location("export_public_candidate", EXPORT_SCRIPT)
assert SPEC is not None and SPEC.loader is not None
export_public_candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(export_public_candidate)


class SponsorSiteContractTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_public_export_includes_the_site(self) -> None:
        self.assertIn("site/", export_public_candidate.PUBLIC_DIRECTORY_PREFIXES)
        for relative in (
            "site/package.json",
            "site/README.md",
            "site/index.html",
            "site/src/App.jsx",
            "site/public/LICENSE.txt",
            "site/public/THIRD_PARTY_NOTICES.txt",
        ):
            self.assertIn(relative, export_public_candidate.REQUIRED_PUBLIC_PATHS)

    def test_site_copy_keeps_open_source_and_sponsorship_boundaries(self) -> None:
        app = self.read("site/src/App.jsx")
        for fragment in (
            "永久免费",
            "MIT 开源",
            "本地优先",
            "赞助不解锁功能、服务或优先级",
            "与学校及教务厂商无官方隶属关系",
            "赞助通道尚未开放",
            "公开仓库尚未发布",
        ):
            self.assertIn(fragment, app)
        self.assertIn('href="./LICENSE.txt"', app)
        self.assertIn('href="./THIRD_PARTY_NOTICES.txt"', app)

        notices = self.read("site/public/THIRD_PARTY_NOTICES.txt")
        self.assertIn("Copyright (c) Meta Platforms, Inc. and affiliates.", notices)
        self.assertIn("Copyright (c) 2020 Phosphor Icons", notices)
        self.assertGreaterEqual(notices.count("Permission is hereby granted"), 2)

    def test_site_has_no_payment_or_tracking_sdk(self) -> None:
        package = json.loads(self.read("site/package.json"))
        dependencies = {
            **package.get("dependencies", {}),
            **package.get("devDependencies", {}),
        }
        forbidden_names = ("stripe", "alipay", "wechat", "analytics", "sentry")
        self.assertFalse(
            any(
                forbidden in dependency.lower()
                for dependency in dependencies
                for forbidden in forbidden_names
            ),
            dependencies,
        )
        source = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted((ROOT / "site/src").glob("*"))
            if path.is_file()
        )
        for forbidden in ("document.cookie", "localStorage", "sessionStorage"):
            self.assertNotIn(forbidden, source)

    def test_site_uses_only_synthetic_schedule_copy(self) -> None:
        app = self.read("site/src/App.jsx")
        for fragment in ("合成课程甲", "合成课程乙", "合成考试甲", "示例校区"):
            self.assertIn(fragment, app)

    def test_environment_example_has_no_real_destination(self) -> None:
        environment = self.read("site/.env.example")
        self.assertEqual(
            environment.splitlines(),
            ["VITE_GITHUB_URL=", "VITE_SPONSOR_URL="],
        )
        self.assertNotIn("VITE_", self.read("site/src/App.jsx"))

    def test_development_server_does_not_bind_every_network_interface(self) -> None:
        config = self.read("site/vite.config.mjs")
        self.assertNotIn("0.0.0.0", config)
        self.assertNotIn("terminal.local", config)

    def test_production_build_is_portable_to_a_static_subpath(self) -> None:
        config = self.read("site/vite.config.mjs")
        app = self.read("site/src/App.jsx")
        index = self.read("site/index.html")
        self.assertIn('base: "./"', config)
        self.assertIn('src="./courseaxis-app-icon.png"', app)
        self.assertNotIn('src="/courseaxis-app-icon.png"', app)
        self.assertIn('rel="icon"', index)
        self.assertIn('href="./courseaxis-app-icon.png"', index)

    def test_supply_chain_gate_audits_development_dependencies(self) -> None:
        validation = self.read("scripts/validate.sh")
        workflow = self.read(".github/workflows/ci.yml")
        for source in (validation, workflow):
            self.assertIn("npm audit --audit-level=high", source)
            self.assertNotIn("npm audit --omit=dev", source)
            self.assertIn("test -s dist/LICENSE.txt", source)
            self.assertIn("test -s dist/THIRD_PARTY_NOTICES.txt", source)
            self.assertIn("python3 scripts/validate-site-build.py", source)


if __name__ == "__main__":
    unittest.main()
