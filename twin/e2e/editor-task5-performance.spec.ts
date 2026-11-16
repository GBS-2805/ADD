import { readFile, writeFile } from "node:fs/promises";
import { expect, test, type Page } from "@playwright/test";

const EVIDENCE = "../artifacts/twin/editor";

test.describe.configure({ timeout: 240_000 });

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

async function open(page: Page, width: number, height: number) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

async function baselineProject(page: Page): Promise<Project> {
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open document menu" }).click();
  await page.getByRole("menuitem", { name: "Save .twin" }).click();
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
      layers,
      selectedLayerIds: [`perf-${count - 1}`],
    },
  };
}

async function openProject(page: Page, project: Project) {
  const chooserPromise = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Open document menu" }).click();
  await page.getByRole("menuitem", { name: "Open .twin" }).click();
  await (await chooserPromise).setFiles({
    name: "performance.twin",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(project)),
  });
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(project.document.layers.length);
}

async function dragAndMeasure(page: Page, width: number, height: number, count: number, label: string) {
  await open(page, width, height);
  const project = withLayers(await baselineProject(page), count);
  await openProject(page, project);
  const layer = page.getByTestId(`art-layer-perf-${count - 1}`);
  const box = await layer.boundingBox();
  if (!box) throw new Error("The performance layer has no box.");
  await page.evaluate(() => {
    const state = window as unknown as { __longTasks: number[]; __longTaskAttribution: string[] };
    state.__longTasks = [];
    state.__longTaskAttribution = [];
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        state.__longTasks.push(entry.duration);
        const attribution = (entry as PerformanceEntry & { attribution?: Array<{ name?: string; containerSelector?: string }> }).attribution ?? [];
        state.__longTaskAttribution.push(attribution.map((item) => `${item.name ?? ""} ${item.containerSelector ?? ""}`.trim()).join(" | "));
      }
    }).observe({ type: "longtask" });
  });
  await page.waitForTimeout(2_000);
  const idleTasks = await page.evaluate(() => {
    const state = window as unknown as { __longTasks: number[]; __longTaskAttribution: string[] };
    const snapshot = { durations: [...state.__longTasks], attribution: [...state.__longTaskAttribution] };
    state.__longTasks = [];
    state.__longTaskAttribution = [];
    return snapshot;
  });
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
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
  const measurement = await page.evaluate(() => {
    const state = window as unknown as { __longTasks: number[]; __longTaskAttribution: string[] };
    return { durations: state.__longTasks, attribution: state.__longTaskAttribution };
  });
  const longTasks = measurement.durations;
  const evidence = {
    label,
    viewport: `${width}x${height}`,
    layers: count,
    elapsedMs,
    idleLongTasks: idleTasks.durations.map((duration) => Math.round(duration * 10) / 10),
    idleMaxLongTaskMs: idleTasks.durations.length ? Math.max(...idleTasks.durations) : 0,
    idleAttribution: [...new Set(idleTasks.attribution)].slice(0, 5),
    longTasks: longTasks.map((duration) => Math.round(duration * 10) / 10),
    maxLongTaskMs: longTasks.length ? Math.max(...longTasks) : 0,
    attribution: [...new Set(measurement.attribution)].slice(0, 5),
  };
  await writeFile(`${EVIDENCE}/performance-${label}.json`, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidence;
}

test("twentyLayerDragHasNoLongTaskOver50ms", async ({ page }) => {
  const phone = await dragAndMeasure(page, 360, 840, 20, "20-layers-360x840");
  expect(phone.maxLongTaskMs).toBeLessThanOrEqual(50);
  const desktop = await dragAndMeasure(page, 1440, 900, 20, "20-layers-1440x900");
  expect(desktop.maxLongTaskMs).toBeLessThanOrEqual(50);
});

test("sixtyFourLayerDragRecordsTheObservedLimit", async ({ page }) => {
  const evidence = await dragAndMeasure(page, 360, 840, 64, "64-layers-360x840");
  expect(evidence.layers).toBe(64);
  expect(evidence.elapsedMs).toBeGreaterThan(8_000);
});
