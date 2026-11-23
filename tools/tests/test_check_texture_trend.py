from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from tools import check_texture_trend


def results_with(lines: list[str]) -> Path:
    root = Path(tempfile.mkdtemp())
    target = root / "test-results" / "testDebugUnitTest"
    target.mkdir(parents=True)
    body = "\n".join(lines)
    (target / "TEST-TexturePresetTest.xml").write_text(
        f"""<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="1" failures="0" errors="0" skipped="0">
  <testcase classname="app.add.engine.TexturePresetTest" name="frameBudget">
    <system-out>{body}</system-out>
  </testcase>
</testsuite>
""",
        encoding="utf-8",
    )
    return target


HOST = ("texture host warmupPasses=25 calibrationMs=5.300 referenceMs=2.650 "
        "factor=2.000 budgetMs=32.000 checksum=1 samplesMs=5.3, 5.3, 5.3")
STALLED = ("texture host warmupPasses=25 calibrationMs=2.600 referenceMs=2.650 "
           "factor=1.000 budgetMs=16.000 checksum=1 samplesMs="
           "2.600, 2.610, 2.620, 2.615, 2.605, 2.625, 2.630, 2.618, 5.878")
BRICK = "texture family=Brick 300x375 warmupPasses=25 bestMs=4.000 samplesMs=2.000, 4.000, 6.000"
WOOD = "texture family=WoodGrain 300x375 warmupPasses=25 bestMs=10.000 samplesMs=8.000, 10.000, 12.000"


class TextureTrendTest(unittest.TestCase):
    def test_measure_returns_host_and_family_medians(self):
        factor, budget, samples, medians = check_texture_trend.measure(
            results_with([HOST, BRICK, WOOD]))
        self.assertEqual(2.0, factor)
        self.assertEqual(32.0, budget)
        self.assertEqual([5.3, 5.3, 5.3], samples)
        self.assertEqual({"Brick": 4.0, "WoodGrain": 10.0}, medians)

    def test_one_stalled_probe_sample_is_trimmed(self):
        target = results_with([STALLED, BRICK, WOOD])
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, check_texture_trend.main(
                ["--results", str(target), "--host-only"]))
        text = output.getvalue()
        self.assertIn("spread=1.010", text)
        self.assertNotIn("WARN", text)

    def test_high_factor_still_warns(self):
        target = results_with([HOST, BRICK, WOOD])
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, check_texture_trend.main(
                ["--results", str(target), "--host-only"]))
        self.assertIn("host factor 2.000 is at or above 2.0", output.getvalue())

    def test_a_family_above_the_ratio_is_flagged(self):
        record = {"warn_ratio": 1.30, "families": {"Brick": 2.0, "WoodGrain": 3.0}}
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            warnings = check_texture_trend.compare(
                {"Brick": 4.0, "WoodGrain": 10.0}, 2.0, record, 1.30)
        self.assertEqual(["WoodGrain"], warnings)
        self.assertIn("Brick medianMs=4.000 normalizedMs=2.000", output.getvalue())
        self.assertIn("WoodGrain", output.getvalue())
        self.assertIn("WARN", output.getvalue())

    def test_three_recent_runs_are_reported_as_sustained(self):
        record = {"warn_ratio": 1.30, "families": {"Brick": 2.0, "WoodGrain": 2.0}}
        history = [
            {"families": {"Brick": 3.0, "WoodGrain": 2.0}},
            {"families": {"Brick": 3.1, "WoodGrain": 3.0}},
        ]
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            sustained = check_texture_trend.sustained_drift(
                history, {"Brick": 4.0, "WoodGrain": 4.0}, 1.0, record, 1.30)
        self.assertEqual(["Brick"], sustained)
        self.assertIn("Brick over 1.30x in 3 of the last 3 runs", output.getvalue())

    def test_one_slow_run_is_not_sustained(self):
        record = {"warn_ratio": 1.30, "families": {"Brick": 2.0}}
        history = [
            {"families": {"Brick": 1.0}},
            {"families": {"Brick": 1.1}},
        ]
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            sustained = check_texture_trend.sustained_drift(
                history, {"Brick": 4.0}, 1.0, record, 1.30)
        self.assertEqual([], sustained)

    def test_main_appends_normalized_history(self):
        target = results_with([HOST, BRICK, WOOD])
        baseline = target.parent / "texture-trend.json"
        history = target.parent / "texture-history.jsonl"
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, check_texture_trend.main([
                "--results", str(target), "--baseline", str(baseline),
                "--history", str(history), "--write", "--no-host"]))
        # --write records the baseline and returns before appending.
        self.assertFalse(history.exists())
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, check_texture_trend.main([
                "--results", str(target), "--baseline", str(baseline),
                "--history", str(history), "--no-host"]))
        entries = check_texture_trend.load_history(history)
        self.assertEqual(1, len(entries))
        self.assertEqual(2.0, entries[0]["families"]["Brick"])
        self.assertEqual(5.0, entries[0]["families"]["WoodGrain"])

    def test_corrupt_history_line_is_ignored(self):
        target = results_with([HOST])
        history = target.parent / "texture-history.jsonl"
        history.write_text(
            "not json\n" + json.dumps({"families": {"Brick": 2.0}}) + "\n",
            encoding="utf-8")
        entries = check_texture_trend.load_history(history)
        self.assertEqual([{"families": {"Brick": 2.0}}], entries)

    def test_write_then_strict_compare(self):
        target = results_with([HOST, BRICK, WOOD])
        baseline = target.parent / "texture-trend.json"
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, check_texture_trend.main(
                ["--results", str(target), "--baseline", str(baseline),
                 "--write", "--no-host"]))
        record = json.loads(baseline.read_text(encoding="utf-8"))
        self.assertEqual(2.0, record["families"]["Brick"])
        self.assertEqual(5.0, record["families"]["WoodGrain"])

        slower = results_with([
            HOST,
            "texture family=Brick 300x375 warmupPasses=25 bestMs=4.000 "
            "samplesMs=4.000, 4.000, 4.000",
            "texture family=WoodGrain 300x375 warmupPasses=25 bestMs=10.000 "
            "samplesMs=20.000, 20.000, 20.000",
        ])
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, check_texture_trend.main(
                ["--results", str(slower), "--baseline", str(baseline), "--no-host"]))
        self.assertIn("WARN", output.getvalue())

        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(1, check_texture_trend.main(
                ["--results", str(slower), "--baseline", str(baseline),
                 "--no-host", "--strict"]))


if __name__ == "__main__":
    unittest.main()
