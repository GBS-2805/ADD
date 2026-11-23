import { mkdir, readFile, writeFile } from "node:fs/promises";
import { expect, test, type Page } from "@playwright/test";
import {
  startFrameProbe,
  stopFrameProbe,
  throttleCpu,
  type FrameStats,
} from "./support/frameProbe";

const EVIDENCE = "../artifacts/twin/editor";
const PERF_RESULTS = "test-results/editor-perf.json";
const CPU_THROTTLE = 4;
const FRAME_P95_BUDGET_MS = 33;
const FRAME_MAX_BUDGET_MS = 50;
/** One 60 Hz interval. Project open must not miss a frame; the drag gates do not use this. */
const ONE_FRAME_MS = 16.8;
/**
 * Promoted on 2026-11-23 from six clean 64-layer runs at 4x CPU: p95 16.7-16.8 ms
 * (one frame interval in every run), max 16.8 / 33.4 / 33.4 / 50.0 / 16.8 /
 * 16.8 ms. The ceilings are the worst observed value plus 10%, rounded up to a
 * tenth: 16.8 * 1.1 = 18.48 -> 18.5; 50.0 * 1.1 = 55. Max is the noisiest
 * statistic, so 55 ms sits only 5 ms above the worst run -- less than one 60 Hz
 * frame. That is a deliberate ratchet: a fourth dropped frame fails. Do not raise
 * either ceiling without three new clean baselines.
 */
const SIXTY_FOUR_LAYER_P95_CEILING_MS = 18.5;
const SIXTY_FOUR_LAYER_MAX_CEILING_MS = 55;

test.describe.configure({ timeout: 240_000 });
// Trace snapshots walk the live DOM on every mutation. That instrumentation is
// valuable on failure, but it would be measured as editor work, so this file runs
// without it and relies on the JSON evidence written next to the assertion.
test.use({ trace: "off" });

interface Project {
  format: string;
  version: number;
  savedAt: string;
  document: {
    background: unknown;
    selectedLayerIds: string[];
    layers: Array<Record<string, unknown> & { id: string; transform: { x: number; y: number } }>;
  };
}

interface DragEvidence {
  label: string;
  viewport: string;
  layers: number;
  elapsedMs: number;
  idleLongTasks: number[];
  idleLongTaskStarts: number[];
  idleMaxLongTaskMs: number;
  idleAttribution: string[];
  longTasks: number[];
  longTaskStarts: number[];
  dragStartedAtMs: number;
  pointerUpAtMs: number | null;
  renderSpans: RenderSpan[];
  longFrames: LongAnimationFrame[];
  maxLongTaskMs: number;
  attribution: string[];
  throttle: number;
  frameStats: FrameStats;
}

interface RenderSpan {
  id: string;
  ms: number;
  atMs: number;
}

interface LongAnimationFrame {
  startTime: number;
  offsetMs: number;
  duration: number;
  renderStart: number;
  styleAndLayoutStart: number;
  blockingDuration: number;
  scripts: Array<{
    invoker: string;
    duration: number;
    forcedStyleAndLayoutDuration: number;
    sourceURL: string;
    sourceFunctionName: string;
  }>;
}

interface PerfResult extends FrameStats {
  case: string;
  throttle: number;
  longTasks: number[];
  maxLongTaskMs: number;
  attribution: string[];
  longTaskStarts: number[];
  elapsedMs: number;
  dragStartedAtMs: number;
  pointerUpAtMs: number | null;
  renderSpans: RenderSpan[];
  longFrames: LongAnimationFrame[];
}

interface OpenEvidence {
  label: string;
  throttle: number;
  longTasks: number[];
  maxLongTaskMs: number;
  renderSpans: RenderSpan[];
  longFrames: LongAnimationFrame[];
  frameStats: FrameStats;
}

async function open(page: Page, width: number, height: number) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

async function baselineProject(page: Page): Promise<Project> {
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open document menu" }).click();
  await page.getByRole("menuitem", { name: "Save .add" }).click();
  const path = await (await downloadPromise).path();
  if (!path) throw new Error("No project download path.");
  return JSON.parse(await readFile(path, "utf8")) as Project;
}

function withLayers(project: Project, count: number): Project {
  const source = project.document.layers.find((layer) => layer.kind === "shape") ?? project.document.layers[0];
  const layers = Array.from({ length: count }, (_, index) => ({
    ...source,
    id: `perf-${index}`,
    name: `Shape / Perf ${index}`,
    effect: null,
    fill: "#111111",
    transform: {
      x: ((index % 5) - 2) * 44,
      y: (Math.floor(index / 5) - Math.floor(count / 10)) * 32,
      scaleX: 0.35,
      scaleY: 0.35,
      rotation: (index % 7) * 9,
    },
  }));
  return {
    ...project,
    document: {
      ...project.document,
      // Isolate layer dragging from animated shader backgrounds: this budget is
      // about the editor's per-pointer-move work, not the GPU renderer.
      background: null,
      layers,
      selectedLayerIds: [`perf-${count - 1}`],
    },
  };
}

async function openProject(
  page: Page,
  project: Project,
  beforeFiles?: () => Promise<void>,
  waitForLayers = true,
) {
  const chooserPromise = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Open document menu" }).click();
  await page.getByRole("menuitem", { name: "Open .add" }).click();
  const chooser = await chooserPromise;
  await beforeFiles?.();
  await chooser.setFiles({
    name: "performance.add",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(project)),
  });
  if (waitForLayers) {
    await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(project.document.layers.length);
  }
}

async function dragAndMeasure(page: Page, width: number, height: number, count: number, label: string, profile = false) {
  if (profile) {
    await page.addInitScript(() => {
      (window as unknown as { __perfProfile?: boolean }).__perfProfile = true;
    });
  }
  await open(page, width, height);
  const project = withLayers(await baselineProject(page), count);
  await openProject(page, project);
  const layer = page.getByTestId(`art-layer-perf-${count - 1}`);
  const box = await layer.boundingBox();
  if (!box) throw new Error("The performance layer has no box.");

  const restoreCpu = await throttleCpu(page, CPU_THROTTLE);
  try {
    await page.evaluate(() => {
      const state = window as unknown as {
        __longTasks: number[];
        __longTaskStarts: number[];
        __longTaskAttribution: string[];
        __longFrames: Array<Record<string, unknown>>;
      };
      state.__longTasks = [];
      state.__longTaskStarts = [];
      state.__longTaskAttribution = [];
      state.__longFrames = [];
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          state.__longTasks.push(entry.duration);
          state.__longTaskStarts.push(Math.round(entry.startTime));
          const attribution = (entry as PerformanceEntry & { attribution?: Array<{ name?: string; containerSelector?: string }> }).attribution ?? [];
          state.__longTaskAttribution.push(attribution.map((item) => `${item.name ?? ""} ${item.containerSelector ?? ""}`.trim()).join(" | "));
        }
      }).observe({ type: "longtask" });
      try {
        new PerformanceObserver((list) => {
          for (const entry of list.getEntries()) {
            const frame = entry as PerformanceEntry & {
              renderStart?: number;
              styleAndLayoutStart?: number;
              blockingDuration?: number;
              scripts?: Array<{
                invoker?: string;
                duration?: number;
                forcedStyleAndLayoutDuration?: number;
                sourceURL?: string;
                sourceFunctionName?: string;
              }>;
            };
            state.__longFrames.push({
              startTime: frame.startTime,
              duration: frame.duration,
              renderStart: frame.renderStart ?? 0,
              styleAndLayoutStart: frame.styleAndLayoutStart ?? 0,
              blockingDuration: frame.blockingDuration ?? 0,
              scripts: (frame.scripts ?? []).map((script) => ({
                invoker: script.invoker ?? "",
                duration: script.duration ?? 0,
                forcedStyleAndLayoutDuration: script.forcedStyleAndLayoutDuration ?? 0,
                sourceURL: script.sourceURL ?? "",
                sourceFunctionName: script.sourceFunctionName ?? "",
              })),
            });
          }
        }).observe({ type: "long-animation-frame" } as PerformanceObserverInit);
      } catch {
        // Chromium-only; the frame probe still locates slow intervals without it.
      }
    });
    await page.waitForTimeout(2_000);
    const idleTasks = await page.evaluate(() => {
      const state = window as unknown as { __longTasks: number[]; __longTaskStarts: number[]; __longTaskAttribution: string[] };
      const snapshot = {
        durations: [...state.__longTasks],
        starts: [...state.__longTaskStarts],
        attribution: [...state.__longTaskAttribution],
      };
      state.__longTasks = [];
      state.__longTaskStarts = [];
      state.__longTaskAttribution = [];
      return snapshot;
    });
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    // Pointerdown selects the layer and begins the session. Let its first commit
    // and the project-open paint settle before sampling: the frame budget is the
    // 50 moves plus pointerup's commit and undo step, not the paint that put the
    // 20-layer project on screen. Pointerup stays inside the window.
    await page.waitForTimeout(1_000);
    const dragStartedAtMs = await page.evaluate(() => {
      const state = window as unknown as {
        __longTasks: number[];
        __longTaskStarts: number[];
        __longTaskAttribution: string[];
        __perfSpans?: unknown[];
        __longFrames?: unknown[];
        __pointerUpAt?: number | null;
      };
      state.__longTasks = [];
      state.__longTaskStarts = [];
      state.__longTaskAttribution = [];
      state.__perfSpans = [];
      state.__longFrames = [];
      state.__pointerUpAt = null;
      window.addEventListener("pointerup", () => {
        (window as unknown as { __pointerUpAt?: number }).__pointerUpAt = performance.now();
      }, { capture: true, once: true });
      return performance.now();
    });
    // The sampler drops its own first three intervals as warm-up, then counts
    // every frame through the drag and pointerup.
    await startFrameProbe(page);
    const start = Date.now();
    for (let index = 1; index <= 50; index += 1) {
      await page.mouse.move(
        box.x + box.width / 2 + 90 * (index / 50),
        box.y + box.height / 2 + 60 * (index / 50),
      );
      await page.waitForTimeout(180);
    }
    const elapsedMs = Date.now() - start;
    await page.mouse.up();
    // Give the pointerup commit two real frames before closing the sampler.
    await page.evaluate(
      () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
    );
    const frameStats = await stopFrameProbe(page);
    // Pointerup commits the transform and its undo step. Give React and the browser
    // one settled frame so the release cost lands in this measurement window.
    await page.waitForTimeout(250);
    const after = await layer.boundingBox();
    if (!after) throw new Error("The performance layer lost its box after the drag.");
    expect(Math.abs(after.x - box.x)).toBeGreaterThan(1);
    const measurement = await page.evaluate(() => {
      const state = window as unknown as {
        __longTasks: number[];
        __longTaskStarts: number[];
        __longTaskAttribution: string[];
        __perfSpans?: Array<{ id: string; ms: number; atMs: number }>;
        __longFrames?: Array<Omit<LongAnimationFrame, "offsetMs">>;
        __pointerUpAt?: number | null;
      };
      return {
        durations: state.__longTasks,
        starts: state.__longTaskStarts,
        attribution: state.__longTaskAttribution,
        spans: state.__perfSpans ?? [],
        longFrames: state.__longFrames ?? [],
        pointerUpAt: state.__pointerUpAt ?? null,
      };
    });
    const longTasks = measurement.durations;
    const evidence: DragEvidence = {
      label,
      viewport: `${width}x${height}`,
      layers: count,
      elapsedMs,
      idleLongTasks: idleTasks.durations.map((duration) => Math.round(duration * 10) / 10),
      idleLongTaskStarts: idleTasks.starts,
      idleMaxLongTaskMs: idleTasks.durations.length ? Math.max(...idleTasks.durations) : 0,
      idleAttribution: [...new Set(idleTasks.attribution)].slice(0, 5),
      longTasks: longTasks.map((duration) => Math.round(duration * 10) / 10),
      longTaskStarts: measurement.starts,
      dragStartedAtMs,
      pointerUpAtMs: measurement.pointerUpAt,
      renderSpans: measurement.spans,
      longFrames: measurement.longFrames.map((frame) => ({
        ...frame,
        offsetMs: Math.round((frame.startTime - dragStartedAtMs) * 10) / 10,
      })),
      maxLongTaskMs: longTasks.length ? Math.max(...longTasks) : 0,
      attribution: [...new Set(measurement.attribution)].slice(0, 5),
      throttle: CPU_THROTTLE,
      frameStats,
    };
    await mkdir(EVIDENCE, { recursive: true });
    await writeFile(`${EVIDENCE}/performance-${label}.json`, `${JSON.stringify(evidence, null, 2)}\n`);
    return evidence;
  } finally {
    await restoreCpu();
  }
}

async function recordOpen(evidence: OpenEvidence) {
  const result = {
    case: evidence.label,
    throttle: evidence.throttle,
    longTasks: evidence.longTasks,
    maxLongTaskMs: evidence.maxLongTaskMs,
    renderSpans: evidence.renderSpans,
    longFrames: evidence.longFrames,
    ...evidence.frameStats,
  };
  let results: Array<Record<string, unknown> & { case: string }> = [];
  try {
    const parsed = JSON.parse(await readFile(PERF_RESULTS, "utf8"));
    if (Array.isArray(parsed)) results = parsed;
  } catch {
    // A first run has no previous results file.
  }
  results = results.filter((item) => item.case !== result.case);
  results.push(result);
  await mkdir("test-results", { recursive: true });
  await writeFile(PERF_RESULTS, `${JSON.stringify(results, null, 2)}\n`);
}

async function record(evidence: DragEvidence) {
  const result: PerfResult = {
    case: evidence.label,
    throttle: evidence.throttle,
    longTasks: evidence.longTasks,
    maxLongTaskMs: evidence.maxLongTaskMs,
    attribution: evidence.attribution,
    longTaskStarts: evidence.longTaskStarts,
    elapsedMs: evidence.elapsedMs,
    dragStartedAtMs: evidence.dragStartedAtMs,
    pointerUpAtMs: evidence.pointerUpAtMs,
    renderSpans: evidence.renderSpans,
    longFrames: evidence.longFrames,
    ...evidence.frameStats,
  };
  let results: PerfResult[] = [];
  try {
    const parsed = JSON.parse(await readFile(PERF_RESULTS, "utf8"));
    if (Array.isArray(parsed)) results = parsed;
  } catch {
    // A first run has no previous results file.
  }
  results = results.filter((item) => item.case !== result.case);
  results.push(result);
  await mkdir("test-results", { recursive: true });
  await writeFile(PERF_RESULTS, `${JSON.stringify(results, null, 2)}\n`);
}

function expectFrameBudget(evidence: DragEvidence) {
  expect(evidence.frameStats.count).toBeGreaterThan(0);
  expect(evidence.frameStats.p95Ms).toBeLessThanOrEqual(FRAME_P95_BUDGET_MS);
  expect(evidence.frameStats.maxMs).toBeLessThanOrEqual(FRAME_MAX_BUDGET_MS);
}

test("twentyLayerDragHoldsTheFrameBudgetAtFourTimesCpu", async ({ page }) => {
  const phone = await dragAndMeasure(page, 360, 840, 20, "20-layers-360x840");
  await record(phone);
  expect(phone.maxLongTaskMs).toBeLessThanOrEqual(50);
  expectFrameBudget(phone);

  const desktop = await dragAndMeasure(page, 1440, 900, 20, "20-layers-1440x900", true);
  await record(desktop);
  expect(desktop.maxLongTaskMs).toBeLessThanOrEqual(50);
  expectFrameBudget(desktop);
});

test("twentyLayerProjectOpensWithinOneFrameAtFourTimesCpu", async ({ page }) => {
  await page.addInitScript(() => {
    (window as unknown as { __perfProfile?: boolean }).__perfProfile = true;
  });
  await open(page, 1440, 900);
  const project = withLayers(await baselineProject(page), 20);
  const restoreCpu = await throttleCpu(page, CPU_THROTTLE);
  try {
    await page.evaluate(() => {
      const state = window as unknown as {
        __perfSpans?: unknown[];
        __longTasks: number[];
        __longTaskStarts: number[];
        __longTaskAttribution: string[];
        __longFrames: Array<Record<string, unknown>>;
      };
      state.__perfSpans = [];
      state.__longTasks = [];
      state.__longTaskStarts = [];
      state.__longTaskAttribution = [];
      state.__longFrames = [];
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          state.__longTasks.push(entry.duration);
          state.__longTaskStarts.push(Math.round(entry.startTime));
        }
      }).observe({ type: "longtask" });
      try {
        new PerformanceObserver((list) => {
          for (const entry of list.getEntries()) {
            const frame = entry as PerformanceEntry & {
              renderStart?: number;
              styleAndLayoutStart?: number;
              blockingDuration?: number;
              scripts?: Array<{
                invoker?: string;
                duration?: number;
                forcedStyleAndLayoutDuration?: number;
                sourceURL?: string;
                sourceFunctionName?: string;
              }>;
            };
            state.__longFrames.push({
              startTime: frame.startTime,
              duration: frame.duration,
              renderStart: frame.renderStart ?? 0,
              styleAndLayoutStart: frame.styleAndLayoutStart ?? 0,
              blockingDuration: frame.blockingDuration ?? 0,
              scripts: (frame.scripts ?? []).map((script) => ({
                invoker: script.invoker ?? "",
                duration: script.duration ?? 0,
                forcedStyleAndLayoutDuration: script.forcedStyleAndLayoutDuration ?? 0,
                sourceURL: script.sourceURL ?? "",
                sourceFunctionName: script.sourceFunctionName ?? "",
              })),
            });
          }
        }).observe({ type: "long-animation-frame" } as PerformanceObserverInit);
      } catch {
        // Chromium-only; frames and spans still locate the open cost.
      }
    });
    // A locator assertion inside the sample polls the live DOM on the same
    // main thread; check the completed reveal after the probe stops instead.
    await openProject(page, project, () => startFrameProbe(page, 0), false);
    // Include the 500 ms autosave that opening the file schedules.
    await page.waitForTimeout(1_000);
    const frameStats = await stopFrameProbe(page);
    await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(project.document.layers.length);
    const measurement = await page.evaluate(() => {
      const state = window as unknown as {
        __perfSpans?: Array<{ id: string; ms: number; atMs: number }>;
        __longTasks?: number[];
        __longFrames?: Array<Record<string, unknown>>;
      };
      return {
        spans: state.__perfSpans ?? [],
        longTasks: state.__longTasks ?? [],
        longFrames: state.__longFrames ?? [],
      };
    });
    const evidence: OpenEvidence = {
      label: "20-layers-open-1440x900",
      throttle: CPU_THROTTLE,
      longTasks: measurement.longTasks,
      maxLongTaskMs: measurement.longTasks.length ? Math.max(...measurement.longTasks) : 0,
      renderSpans: measurement.spans,
      longFrames: measurement.longFrames as unknown as LongAnimationFrame[],
      frameStats,
    };
    await recordOpen(evidence);
    expect(evidence.frameStats.count).toBeGreaterThan(0);
    expect(evidence.maxLongTaskMs).toBeLessThanOrEqual(50);
    expect(evidence.frameStats.maxMs).toBeLessThanOrEqual(ONE_FRAME_MS);
  } finally {
    await restoreCpu();
  }
});

test("sixtyFourLayerDragHoldsTheMeasuredFrameCeilingAtFourTimesCpu", async ({ page }) => {
  const evidence = await dragAndMeasure(page, 360, 840, 64, "64-layers-360x840");
  await record(evidence);
  expect(evidence.layers).toBe(64);
  expect(evidence.elapsedMs).toBeGreaterThan(8_000);
  expect(evidence.frameStats.count).toBeGreaterThan(0);
  // Both assertions are required: p95 alone hides one long stall, max alone
  // hides a run full of merely slow frames.
  expect(evidence.frameStats.p95Ms).toBeLessThanOrEqual(SIXTY_FOUR_LAYER_P95_CEILING_MS);
  expect(evidence.frameStats.maxMs).toBeLessThanOrEqual(SIXTY_FOUR_LAYER_MAX_CEILING_MS);
});
