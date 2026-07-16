import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class PublicRepositoryDocumentationTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_readme_describes_ci_and_release_boundaries(self) -> None:
        readme = self.read("README.md")
        for fragment in (
            "## 持续集成与发布边界",
            ".github/workflows/ci.yml",
            ".github/workflows/codeql.yml",
            "不保存发布签名凭据",
            "Android 16",
            "macOS 26",
        ):
            self.assertIn(fragment, readme)

    def test_acceptance_covers_provider_and_public_candidate(self) -> None:
        acceptance = self.read("docs/ACCEPTANCE.md")
        for fragment in (
            "## P. Provider 与学期隔离",
            "## Q. 公开候选与发行边界",
            "public hygiene",
            "fresh clone",
            "不包含签名私钥",
        ):
            self.assertIn(fragment, acceptance)

    def test_architecture_and_provider_docs_describe_automated_gates(self) -> None:
        architecture = self.read("docs/ARCHITECTURE.md")
        provider = self.read("docs/PROVIDER_DEVELOPMENT.md")
        self.assertIn("## 13. 持续集成与发行信任边界", architecture)
        self.assertIn("40 位提交 SHA", architecture)
        self.assertIn("Public hygiene and contracts", provider)
        self.assertIn("Review dependency graph changes", provider)

    def test_project_remains_free_without_sponsor_entitlements(self) -> None:
        readme = self.read("README.md")
        self.assertIn("维护者提供的软件本身永久免费", readme)
        self.assertIn("自愿赞助不解锁功能、服务或优先级", readme)

    def test_python_validation_artifacts_are_ignored(self) -> None:
        ignore = self.read(".gitignore").splitlines()
        self.assertIn("__pycache__/", ignore)
        self.assertIn("*.py[cod]", ignore)


if __name__ == "__main__":
    unittest.main()
