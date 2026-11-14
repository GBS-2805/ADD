from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_dependencies


def make_tree(root: Path) -> None:
    (root / "tools").mkdir()
    (root / "tools" / "deploy_vercel.py").write_text(
        'CLI = ("npx", "-y", "vercel@65.14.0")\n', encoding="utf-8")
    (root / ".github" / "workflows").mkdir(parents=True)
    (root / ".github" / "workflows" / "checks.yml").write_text(
        'run: npm install --prefix /tmp/pw "playwright@1.65.0"\n', encoding="utf-8")
    engine = root / "tools" / "engine-spike" / "gradle" / "wrapper"
    engine.mkdir(parents=True)
    (engine / "gradle-wrapper.properties").write_text(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.9-bin.zip\n"
        "distributionSha256Sum=%s\n" % check_dependencies.GRADLE_SHA, encoding="utf-8")
    (root / "tools" / "engine-spike" / "app").mkdir()
    (root / "tools" / "engine-spike" / "app" / "build.gradle.kts").write_text(
        'dependencies { implementation("androidx.core:core-ktx:1.15.0") }\n',
        encoding="utf-8")


class DependencyTests(unittest.TestCase):
    def test_safe_tree_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            self.assertEqual(check_dependencies.problems(root), [])

    def test_unpinned_vercel_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            (root / "tools" / "deploy_vercel.py").write_text(
                'CLI = ("npx", "-y", "vercel@latest")\n', encoding="utf-8")
            found = check_dependencies.problems(root)
            self.assertTrue(any("vercel@latest" in item for item in found), found)

    def test_wrong_playwright_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            (root / ".github" / "workflows" / "checks.yml").write_text(
                'run: npm install "playwright@1.64.0"\n', encoding="utf-8")
            found = check_dependencies.problems(root)
            self.assertTrue(any("playwright@1.64.0" in item for item in found), found)

    def test_missing_gradle_checksum_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            wrapper = (root / "tools" / "engine-spike" / "gradle" / "wrapper"
                       / "gradle-wrapper.properties")
            wrapper.write_text(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.9-bin.zip\n",
                encoding="utf-8")
            found = check_dependencies.problems(root)
            self.assertTrue(any("distributionSha256Sum" in item for item in found), found)

    def test_floating_gradle_version_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            (root / "tools" / "engine-spike" / "app" / "build.gradle.kts").write_text(
                'dependencies { implementation("com.example:lib:1.+") }\n',
                encoding="utf-8")
            found = check_dependencies.problems(root)
            self.assertTrue(any("floating dependency" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
