import io
import tempfile
import unittest
from pathlib import Path

from tools import cleanup_waves


class CleanupWavesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, relative, data=b"x"):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def test_protected_paths_are_never_planned(self):
        self.put("docs/index.html")
        self.put("artifacts/bmo/report.json")
        current = self.put("dist/ADD-3.9.apk")
        self.put("dist/old.apk")
        entries = cleanup_waves.wave_entries(1, self.root)
        self.assertNotIn(current, entries)
        self.assertNotIn((self.root / "docs/index.html").resolve(), entries)
        self.assertTrue(cleanup_waves.protected(self.root / "docs/index.html", self.root))
        self.assertTrue(cleanup_waves.protected(self.root / "artifacts/bmo/report.json", self.root))
        self.assertTrue(cleanup_waves.protected(current, self.root))

    def test_wave_one_keeps_the_current_apks(self):
        self.put("dist/old.apk")
        self.put("dist/ADD-3.9.apk")
        self.put("artifacts/handoff-apk/old.apk")
        self.put("artifacts/handoff-apk/ADD-3.9.1-field-preview.apk")
        entries = {path.relative_to(self.root).as_posix()
                   for path in cleanup_waves.wave_entries(1, self.root)}
        self.assertIn("dist/old.apk", entries)
        self.assertNotIn("dist/ADD-3.9.apk", entries)
        self.assertIn("artifacts/handoff-apk/old.apk", entries)
        self.assertNotIn("artifacts/handoff-apk/ADD-3.9.1-field-preview.apk", entries)

    def test_wave_two_targets_only_build_trees(self):
        for relative in ("tools/engine-spike/app/build/out.bin",
                         "tools/engine-review/app/build/out.bin",
                         "build/out.bin", "app/assets/www/index.html"):
            self.put(relative)
        self.put("tools/engine-spike/app/src/Main.kt")
        entries = {path.relative_to(self.root).as_posix()
                   for path in cleanup_waves.wave_entries(2, self.root)}
        self.assertEqual(entries, {
            "app/assets/www", "build",
            "tools/engine-review/app/build", "tools/engine-spike/app/build",
        })

    def test_dry_run_changes_nothing(self):
        old = self.put("dist/old.apk", b"old")
        out = io.StringIO()
        cleanup_waves.main(["--wave", "1", "--dry-run"], root=self.root, out=out)
        self.assertTrue(old.exists())
        self.assertIn("remove 3 dist/old.apk", out.getvalue())

    def test_apply_removes_only_planned_entries(self):
        old = self.put("dist/old.apk", b"old")
        current = self.put("dist/ADD-3.9.apk", b"current")
        unrelated = self.put("docs/index.html", b"docs")
        removed, size = cleanup_waves.apply([old], self.root)
        self.assertEqual((removed, size), (1, 3))
        self.assertFalse(old.exists())
        self.assertTrue(current.exists())
        self.assertTrue(unrelated.exists())

    def test_path_outside_root_is_rejected(self):
        with self.assertRaises(ValueError):
            cleanup_waves.apply([Path("/etc/passwd")], self.root)

    def test_symlink_is_not_followed(self):
        target = self.put("outside/secret.txt", b"secret")
        link = self.root / "dist/link.apk"
        link.parent.mkdir(exist_ok=True)
        link.symlink_to(target)
        self.assertNotIn(link, cleanup_waves.wave_entries(1, self.root))
        with self.assertRaises(ValueError):
            cleanup_waves.apply([link], self.root)
        self.assertTrue(target.exists())

    def test_unknown_wave_is_an_error(self):
        with self.assertRaises(ValueError):
            cleanup_waves.wave_entries(99, self.root)

    def test_tracked_source_is_refused(self):
        source = cleanup_waves.ROOT / "tools/cleanup_waves.py"
        self.assertTrue(cleanup_waves.protected(source, cleanup_waves.ROOT))


if __name__ == "__main__":
    unittest.main()
