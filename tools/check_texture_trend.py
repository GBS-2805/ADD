#!/usr/bin/env python3
"""Report the engine texture host's health and compare per-family medians.

The frame-budget gate fails only when a family crosses the host-scaled 16 ms
budget. A family that quietly doubles from 4 ms to 8 ms still passes, so this
report compares the median of the nine CPU-time samples for every family with
the median recorded at ``tools/engine-spike/texture-trend.json``. The medians
are divided by the run's host factor, which is the same scale the budget uses.

The host report uses a trimmed spread: with at least five samples it drops the
single lowest and single highest, so one descheduled probe sample cannot make a
healthy host look unstable. The factor itself already uses the minimum sample.

    python3 tools/check_texture_trend.py
    python3 tools/check_texture_trend.py --host-only   # no baseline needed
    python3 tools/check_texture_trend.py --write       # record the current run

It is report-only: a change above ``warn_ratio`` prints ``WARN`` and the final
line counts it, but the exit status stays 0 so a noisy host cannot fail a push.
``--strict`` turns those warnings into exit status 1 for a future enforcing run.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "tools" / "engine-spike" / "app" / "build" / "test-results" / "testDebugUnitTest"
BASELINE = ROOT / "tools" / "engine-spike" / "texture-trend.json"

FAMILY = re.compile(r"texture family=(\S+) .*samplesMs=(.*)$")
FACTOR = re.compile(r"factor=([0-9.]+)")
BUDGET = re.compile(r"budgetMs=([0-9.]+)")
SAMPLES = re.compile(r"samplesMs=(.*)$")
NUMBER = re.compile(r"[0-9]+(?:\.[0-9]+)?")


def measure(results: Path) -> tuple[float | None, float | None, list[float], dict[str, float]]:
    """Return the host factor, budget, probe samples and family medians."""
    factor = None
    budget = None
    host_samples: list[float] = []
    samples: dict[str, list[float]] = {}
    for path in sorted(Path(results).rglob("*.xml")):
        try:
            suite = ET.parse(path).getroot()
        except (OSError, ET.ParseError):
            continue
        for out in suite.iter("system-out"):
            for raw in (out.text or "").splitlines():
                line = raw.strip()
                if line.startswith("texture host "):
                    match = FACTOR.search(line)
                    if match:
                        factor = float(match.group(1))
                    match = BUDGET.search(line)
                    if match:
                        budget = float(match.group(1))
                    match = SAMPLES.search(line)
                    if match:
                        host_samples = [float(value) for value in NUMBER.findall(match.group(1))]
                    continue
                match = FAMILY.search(line)
                if not match:
                    continue
                values = [float(value) for value in NUMBER.findall(match.group(2))]
                if values:
                    samples[match.group(1)] = values
    medians = {name: statistics.median(values) for name, values in samples.items()}
    return factor, budget, host_samples, medians


def trimmed_spread(samples: list[float]) -> float:
    """Max/min after dropping one extreme at each end when there are enough samples."""
    ordered = sorted(value for value in samples if value > 0)
    if len(ordered) >= 5:
        ordered = ordered[1:-1]
    if len(ordered) < 2:
        return 0.0
    return ordered[-1] / ordered[0]


def report_host(factor: float, budget: float | None, samples: list[float]) -> tuple[float, list[str]]:
    """Print the host line and summary block; return the trimmed spread and warnings."""
    spread = trimmed_spread(samples)
    warnings = []
    if factor >= 2.0:
        warnings.append("host factor %.3f is at or above 2.0" % factor)
    if spread >= 1.25:
        warnings.append("trimmed probe spread %.3f is at or above 1.25" % spread)
    print("engine-host: factor=%.3f budgetMs=%s spread=%.3f"
          % (factor, "?" if budget is None else "%.3f" % budget, spread))
    if warnings:
        print("engine-host: WARN " + "; ".join(warnings))

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        calibration = min(samples) if samples else None
        with open(summary, "a", encoding="utf-8") as out:
            out.write("### Texture host\n")
            out.write("- factor: %.3f\n" % factor)
            out.write("- budget: %s ms\n" % ("?" if budget is None else "%.3f" % budget))
            out.write("- calibration: %s ms\n"
                      % ("?" if calibration is None else "%.3f" % calibration))
            out.write("- trimmed probe spread: %.3f\n" % spread)
            if warnings:
                out.write("- **WARN**: %s\n" % "; ".join(warnings))
    return spread, warnings


def compare(medians: dict[str, float], factor: float, record: dict,
            warn_ratio: float) -> list[str]:
    """Print one line per family and return the names above the warning ratio."""
    warnings = []
    baseline = record.get("families", {})
    for name in sorted(medians):
        normalized = medians[name] / max(factor, 1.0)
        base = baseline.get(name)
        if base is None:
            print("engine-trend: %s normalizedMs=%.3f has no baseline" % (name, normalized))
            continue
        ratio = normalized / base if base > 0 else float("inf")
        flagged = ratio >= warn_ratio
        if flagged:
            warnings.append(name)
        print(
            "engine-trend: %s medianMs=%.3f normalizedMs=%.3f baselineMs=%.3f "
            "ratio=%.3f%s"
            % (name, medians[name], normalized, base, ratio, " WARN" if flagged else "")
        )
    for name in sorted(set(baseline) - set(medians)):
        print("engine-trend: %s has a baseline but no sample in this run" % name)
    return warnings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--results", type=Path, default=RESULTS,
                        help="directory containing the engine JUnit XML")
    parser.add_argument("--baseline", type=Path, default=BASELINE,
                        help="checked-in texture baseline")
    parser.add_argument("--write", action="store_true",
                        help="record the current medians as the baseline")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 when a family is above the warning ratio")
    parser.add_argument("--host-only", action="store_true",
                        help="print the host report and stop; no baseline needed")
    parser.add_argument("--no-host", action="store_true",
                        help="skip the host report when comparing families")
    args = parser.parse_args(argv)

    factor, budget, host_samples, medians = measure(args.results)
    if factor is None:
        print("engine-host: no texture host line under %s" % args.results)
        return 2
    if not args.no_host:
        report_host(factor, budget, host_samples)
    if args.host_only:
        return 0
    if not medians:
        print("engine-trend: no family samples under %s" % args.results)
        return 2

    if args.write:
        record = {
            "note": ("Median of nine CPU-time samples per family at 300x375, "
                     "divided by the run's host factor. Report-only baseline."),
            "reference_ms": 2.65,
            "warn_ratio": 1.30,
            "recorded": time.strftime("%Y-%m-%d"),
            "families": {name: round(value / max(factor, 1.0), 3)
                         for name, value in sorted(medians.items())},
        }
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        print("engine-trend: wrote %d families to %s" % (len(medians), args.baseline))
        return 0

    if not args.baseline.is_file():
        print("engine-trend: no baseline at %s; run --write" % args.baseline)
        return 2
    try:
        record = json.loads(args.baseline.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print("engine-trend: cannot read %s (%s)" % (args.baseline, error))
        return 2

    warn_ratio = float(record.get("warn_ratio", 1.30))
    warnings = compare(medians, factor, record, warn_ratio)
    print("engine-trend: %d families, %d above %.2fx"
          % (len(medians), len(warnings), warn_ratio))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("### Texture families\n")
            out.write("| family | median ms | normalized ms |\n")
            out.write("| --- | ---: | ---: |\n")
            for name in sorted(medians):
                out.write("| %s | %.3f | %.3f |\n"
                          % (name, medians[name], medians[name] / max(factor, 1.0)))
            if warnings:
                out.write("\n**WARN** above %.2fx: %s\n"
                          % (warn_ratio, ", ".join(sorted(warnings))))
    if warnings:
        print("engine-trend: WARN " + ", ".join(sorted(warnings)))
        return 1 if args.strict else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
