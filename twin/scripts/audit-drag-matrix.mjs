import { chromium } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const BASE_URL = process.env.AUDIT_BASE_URL ?? "https://site-b5c3eb4162ed46148d22881fe0e65300.freebuff.page/";
const LAYER = process.env.AUDIT_LAYER ?? "text";
const OUT = path.resolve(process.env.AUDIT_OUT ?? "../artifacts/twin/effect-audit-deployed/drag");
const VIEWPORT = { width: 390, height: 844 };
const DRAG = { x: 22, y: -12 };
const LAYER_OUT = path.join(OUT, LAYER);

const EFFECTS = [
  { key: "shader-mesh", plugin: "shader-lab", label: "Mesh gradient", apply: (page) => page.getByTestId("shader-preset-mesh").click(), wait: 600 },
  { key: "shader-grain", plugin: "shader-lab", label: "Grain gradient", apply: (page) => page.getByTestId("shader-preset-grain").click(), wait: 600 },
  { key: "shader-warp", plugin: "shader-lab", label: "Warp", apply: (page) => page.getByTestId("shader-preset-warp").click(), wait: 600 },
  { key: "shader-orbit", plugin: "shader-lab", label: "Dot orbit", apply: (page) => page.getByTestId("shader-preset-orbit").click(), wait: 600 },
  { key: "shader-panels", plugin: "shader-lab", label: "Ink panels", apply: (page) => page.getByTestId("shader-preset-panels").click(), wait: 800 },
  { key: "shader-metal", plugin: "shader-lab", label: "Liquid metal", apply: (page) => page.getByTestId("shader-preset-metal").click(), wait: 700 },
  { key: "shader-smoke", plugin: "shader-lab", label: "Gem smoke", apply: (page) => page.getByTestId("shader-preset-smoke").click(), wait: 700 },
  { key: "shader-paper", plugin: "shader-lab", label: "Paper texture", apply: (page) => page.getByTestId("shader-preset-paper").click(), wait: 700 },
  { key: "texture-paper", plugin: "texture-lab", label: "Paper 006", apply: (page) => page.getByTestId("texture-preset-paper").click(), wait: 700 },
  { key: "texture-creased", plugin: "texture-lab", label: "Fluted glass", apply: (page) => page.getByTestId("texture-preset-creased").click(), wait: 700 },
  { key: "texture-dither", plugin: "texture-lab", label: "Image dither", apply: (page) => page.getByTestId("texture-preset-dither").click(), wait: 700 },
  { key: "texture-halftone", plugin: "texture-lab", label: "Halftone dots", apply: (page) => page.getByTestId("texture-preset-halftone").click(), wait: 700 },
  { key: "texture-fur", plugin: "texture-lab", label: "Fur", apply: (page) => page.getByTestId("texture-preset-fur").click(), wait: 800 },
  { key: "particles-dust", plugin: "particle-field", label: "Dust", apply: (page) => page.getByTestId("particle-preset-dust").click(), wait: 300 },
  { key: "particles-sparks", plugin: "particle-field", label: "Sparks", apply: (page) => page.getByTestId("particle-preset-sparks").click(), wait: 300 },
  { key: "particles-orbit", plugin: "particle-field", label: "Orbit", apply: (page) => page.getByTestId("particle-preset-orbit").click(), wait: 300 },
  { key: "particles-confetti", plugin: "particle-field", label: "Confetti", apply: (page) => page.getByTestId("particle-preset-confetti").click(), wait: 300 },
  { key: "pattern-jigsaw", plugin: "pattern-library", label: "Jigsaw", apply: (page) => page.getByTestId("pattern-preset-jigsaw").click(), wait: 250 },
  { key: "pattern-topography", plugin: "pattern-library", label: "Topography", apply: (page) => page.getByTestId("pattern-preset-topography").click(), wait: 250 },
  { key: "pattern-circuit", plugin: "pattern-library", label: "Circuit", apply: (page) => page.getByTestId("pattern-preset-circuit").click(), wait: 250 },
  { key: "pattern-diagonal", plugin: "pattern-library", label: "Diagonal", apply: (page) => page.getByTestId("pattern-preset-diagonal").click(), wait: 250 },
  { key: "pattern-plus", plugin: "pattern-library", label: "Plus", apply: (page) => page.getByTestId("pattern-preset-plus").click(), wait: 250 },
  { key: "pattern-rain", plugin: "pattern-library", label: "Rain", apply: (page) => page.getByTestId("pattern-preset-rain").click(), wait: 250 },
  { key: "pattern-bubbles", plugin: "pattern-library", label: "Bubbles", apply: (page) => page.getByTestId("pattern-preset-bubbles").click(), wait: 250 },
  { key: "pattern-aztec", plugin: "pattern-library", label: "Aztec", apply: (page) => page.getByTestId("pattern-preset-aztec").click(), wait: 250 },
  { key: "native-tone-paper", plugin: "native-parity", label: "Paper tone", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Tone", exact: true }).click();
    await plugin.locator(".tone-card").filter({ hasText: "Paper" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-tone-plum", plugin: "native-parity", label: "Plum tone", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Tone", exact: true }).click();
    await plugin.locator(".tone-card").filter({ hasText: "Plum" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-tone-night", plugin: "native-parity", label: "Night tone", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Tone", exact: true }).click();
    await plugin.locator(".tone-card").filter({ hasText: "Night" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-tone-dim", plugin: "native-parity", label: "Dim tone", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Tone", exact: true }).click();
    await plugin.locator(".tone-card").filter({ hasText: "Dim" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-tone-rose", plugin: "native-parity", label: "Rose tone", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Tone", exact: true }).click();
    await plugin.locator(".tone-card").filter({ hasText: "Rose" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-prism-aztec", plugin: "native-parity", label: "Aztec prism", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Prism", exact: true }).click();
    await plugin.locator(".prism-row").filter({ hasText: "Aztec field" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-prism-topography", plugin: "native-parity", label: "Contour prism", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Prism", exact: true }).click();
    await plugin.locator(".prism-row").filter({ hasText: "Contour" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "native-prism-circuit", plugin: "native-parity", label: "Circuit prism", apply: async (page) => {
    const plugin = page.getByTestId("plugin-native-parity");
    await plugin.getByRole("button", { name: "Prism", exact: true }).click();
    await plugin.locator(".prism-row").filter({ hasText: "Circuit" }).click();
    await plugin.locator(".button.primary").click();
  }, wait: 300 },
  { key: "spatial-ground", plugin: "spatial-dock", label: "Spatial ground", apply: (page) => page.getByRole("button", { name: "Send a motion ground" }).click(), wait: 400 },
  { key: "asset-paper-006", plugin: "asset-shelf", label: "Asset Paper 006", apply: (page) => page.locator(".paper-card").filter({ hasText: "Paper 006" }).click(), wait: 700 },
  { key: "asset-paper-005", plugin: "asset-shelf", label: "Asset Paper 005", apply: (page) => page.locator(".paper-card").filter({ hasText: "Paper 005" }).click(), wait: 700 },
];

function moduleRoot() {
  return path.dirname(new URL(import.meta.url).pathname);
}

async function exists(target) {
  try {
    await fs.access(target);
    return true;
  } catch {
    return false;
  }
}

await fs.mkdir(LAYER_OUT, { recursive: true });
const errors = [];
const result = {
  baseUrl: BASE_URL,
  viewport: VIEWPORT,
  layer: LAYER,
  drag: DRAG,
  startedAt: new Date().toISOString(),
  baseline: null,
  rows: [],
  errors,
};

const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
});
const context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1 });
const page = await context.newPage();
page.setDefaultTimeout(30_000);
page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));
page.on("console", (message) => {
  if (message.type() === "error") errors.push(`console: ${message.text()}`);
});

const artboard = page.getByTestId("artboard");
const panel = page.getByTestId("panel-sheet");

async function screenshot(target, file) {
  await target.screenshot({ path: file });
}

async function closePanel() {
  if (!(await panel.evaluate((element) => element.classList.contains("is-open")).catch(() => false))) return;
  await page.getByRole("button", { name: "Close panel" }).click();
  await page.waitForFunction(() => !document.querySelector('[data-testid="panel-sheet"]')?.classList.contains("is-open"));
  await page.waitForTimeout(220);
}

async function openPlugin(id) {
  await closePanel();
  await page.getByRole("button", { name: "Open the plugin catalogue" }).click();
  await page.getByTestId("plugin-catalogue").waitFor({ state: "visible" });
  await page.getByTestId(`catalogue-${id}`).click();
  const plugin = page.getByTestId(`plugin-${id}`);
  await plugin.waitFor({ state: "visible" });
  const target = plugin.getByRole("button", { name: "Layer", exact: true });
  if ((await target.count()) && (await target.getAttribute("aria-pressed")) !== "true") {
    await target.click();
  }
  return plugin;
}

async function selectLayer(layer, id) {
  await closePanel();
  if (!(await layer.evaluate((element) => element.classList.contains("is-selected")))) {
    await layer.click({ force: true });
    await page.waitForTimeout(160);
  }
  await closePanel();
  if (!(await layer.evaluate((element) => element.classList.contains("is-selected")))) {
    throw new Error(`Layer ${id} is not selected.`);
  }
}

async function layerBox(layer) {
  const value = await layer.boundingBox();
  if (!value) throw new Error("The layer has no layout box.");
  return value;
}

async function effectSnapshot(layer, id) {
  const effect = page.getByTestId(`layer-effect-${id}`);
  if (!(await effect.count())) return { exists: false, visible: false, box: null };
  const visible = await effect.isVisible();
  const box = await effect.boundingBox();
  const canvases = effect.locator("canvas");
  const canvasSize = (await canvases.count())
    ? await canvases.first().evaluate((element) => ({ width: element.width, height: element.height }))
    : null;
  return {
    exists: true,
    visible,
    box,
    canvasSize,
    childCount: await effect.locator("canvas, .pattern-surface, .effect-particles, .effect-fur, .effect-solid, .effect-texture").count(),
    surface: await effect.evaluate((element) => element.firstElementChild?.className ?? null),
    mixBlendMode: await effect.evaluate((element) => getComputedStyle(element).mixBlendMode),
  };
}

function boxDelta(a, b) {
  if (!a || !b) return null;
  return {
    x: Number((b.x - a.x).toFixed(3)),
    y: Number((b.y - a.y).toFixed(3)),
    width: Number((b.width - a.width).toFixed(3)),
    height: Number((b.height - a.height).toFixed(3)),
  };
}

async function dragTo(layer, dx, dy) {
  const start = await layerBox(layer);
  const x = start.x + start.width / 2;
  const y = start.y + start.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y + dy, { steps: 10 });
  await page.waitForTimeout(130);
  return start;
}

async function dragBack(layer, dx, dy) {
  const current = await layerBox(layer);
  const x = current.x + current.width / 2;
  const y = current.y + current.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x - dx, y - dy, { steps: 10 });
  await page.mouse.up();
  await page.waitForTimeout(220);
}

async function captureBaseline(layer, id) {
  const p0 = await layerBox(layer);
  await screenshot(artboard, path.join(LAYER_OUT, "baseline-p0.png"));
  const p1 = await dragTo(layer, DRAG.x, DRAG.y);
  const mid = [];
  for (let index = 1; index <= 3; index += 1) {
    const file = path.join(LAYER_OUT, `baseline-mid-${index}.png`);
    await screenshot(artboard, file);
    mid.push({
      file: path.relative(OUT, file),
      layerBox: await layerBox(layer),
      effect: await effectSnapshot(layer, id),
    });
    await page.waitForTimeout(90);
  }
  await page.mouse.up();
  await page.waitForTimeout(220);
  const afterFile = path.join(LAYER_OUT, "baseline-after.png");
  await screenshot(artboard, afterFile);
  const after = { file: path.relative(OUT, afterFile), layerBox: await layerBox(layer), effect: await effectSnapshot(layer, id) };
  await dragBack(layer, DRAG.x, DRAG.y);
  const reset = await layerBox(layer);
  if (Math.abs(reset.x - p0.x) > 1 || Math.abs(reset.y - p0.y) > 1) {
    throw new Error(`Layer did not reset to its baseline position: start ${JSON.stringify(p0)}, reset ${JSON.stringify(reset)}`);
  }
  return { p0, dragStart: p1, mid, after, reset };
}

async function captureEffect(layer, id, effect) {
  const p0 = await layerBox(layer);
  await screenshot(artboard, path.join(LAYER_OUT, `${effect.key}-static-p0.png`));
  await openPlugin(effect.plugin);
  await effect.apply(page);
  await page.waitForTimeout(effect.wait ?? 500);
  await closePanel();
  await page.getByTestId(`layer-effect-${id}`).waitFor({ state: "visible" });
  await page.waitForTimeout(180);
  await screenshot(artboard, path.join(LAYER_OUT, `${effect.key}-static.png`));
  const start = await dragTo(layer, DRAG.x, DRAG.y);
  const frames = [];
  for (let index = 1; index <= 3; index += 1) {
    const file = path.join(LAYER_OUT, `${effect.key}-mid-${index}.png`);
    await screenshot(artboard, file);
    const layerAtFrame = await layerBox(layer);
    const effectAtFrame = await effectSnapshot(layer, id);
    frames.push({
      phase: `mid-${index}`,
      file: path.relative(OUT, file),
      layerBox: layerAtFrame,
      effect: effectAtFrame,
      delta: boxDelta(layerAtFrame, effectAtFrame.box),
      dragging: (await layer.getAttribute("data-dragging")) === "true",
    });
    await page.waitForTimeout(90);
  }
  await page.mouse.up();
  await page.waitForTimeout(220);
  const afterFile = path.join(LAYER_OUT, `${effect.key}-after.png`);
  await screenshot(artboard, afterFile);
  const layerAfter = await layerBox(layer);
  const effectAfter = await effectSnapshot(layer, id);
  const after = {
    phase: "after",
    file: path.relative(OUT, afterFile),
    layerBox: layerAfter,
    effect: effectAfter,
    delta: boxDelta(layerAfter, effectAfter.box),
    dragging: (await layer.getAttribute("data-dragging")) === "true",
  };

  // Move back before undo. Undo restores the pre-effect document snapshot, so
  // undoing first would also restore the old transform and make a cleanup drag
  // apply twice. The after frame above is captured before this cleanup.
  await dragBack(layer, DRAG.x, DRAG.y);
  const reset = await layerBox(layer);
  if (Math.abs(reset.x - p0.x) > 1 || Math.abs(reset.y - p0.y) > 1) {
    throw new Error(`${effect.key}: layer did not reset after effect: start ${JSON.stringify(p0)}, reset ${JSON.stringify(reset)}`);
  }

  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByTestId("command-sheet").waitFor({ state: "visible" });
  await page.getByRole("button", { name: /Undo last edit/ }).click();
  await page.waitForTimeout(160);
  await closePanel();
  const remaining = await page.getByTestId(`layer-effect-${id}`).count();
  if (remaining !== 0) throw new Error(`${effect.key}: undo left ${remaining} effect surface(s) mounted.`);

  return { key: effect.key, label: effect.label, plugin: effect.plugin, start, frames, after, reset };
}

async function setupLayer() {
  if (LAYER === "text") {
    const layer = page.getByTestId("art-layer-type-open-studio");
    await selectLayer(layer, "type-open-studio");
    return { layer, id: "type-open-studio", kind: "text" };
  }
  if (LAYER === "shape") {
    const layer = page.getByTestId("art-layer-shape-star");
    await selectLayer(layer, "shape-star");
    return { layer, id: "shape-star", kind: "shape" };
  }
  if (LAYER === "image") {
    await page.getByRole("button", { name: "Add a layer" }).click();
    await page.getByRole("menuitem", { name: /^PNG/ }).click();
    await page.getByTestId("plugin-overlay-lab").waitFor({ state: "visible" });
    await page.getByTestId("overlay-import-input").setInputFiles(path.join(moduleRoot(), "..", "e2e", "fixtures", "layer.png"));
    await page.getByTestId("inspector").waitFor({ state: "visible" });
    const layer = page.locator(".art-layer-image").first();
    const testId = await layer.getAttribute("data-testid");
    if (!testId) throw new Error("The imported PNG has no test id.");
    const id = testId.replace(/^art-layer-/, "");
    await closePanel();
    return { layer, id, kind: "image" };
  }
  throw new Error(`Unknown AUDIT_LAYER: ${LAYER}`);
}

try {
  await page.goto(BASE_URL, { waitUntil: "load" });
  await page.waitForTimeout(900);
  await page.evaluate(() => document.fonts.ready);
  await closePanel();
  const setup = await setupLayer();
  result.kind = setup.kind;
  result.layerId = setup.id;
  result.initialBox = await layerBox(setup.layer);
  result.baseline = await captureBaseline(setup.layer, setup.id);
  if (result.baseline.mid.some((frame) => frame.effect.exists)) {
    throw new Error("The baseline has an effect mounted.");
  }

  for (let index = 0; index < EFFECTS.length; index += 1) {
    const effect = EFFECTS[index];
    process.stdout.write(`[${LAYER} ${index + 1}/${EFFECTS.length}] ${effect.key} ... `);
    const row = await captureEffect(setup.layer, setup.id, effect);
    result.rows.push(row);
    await fs.writeFile(path.join(OUT, `matrix-${LAYER}.json`), JSON.stringify(result, null, 2));
    console.log(`captured ${row.frames.length} mid + after`);
  }

  result.completedAt = new Date().toISOString();
  await fs.writeFile(path.join(OUT, `matrix-${LAYER}.json`), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ ok: errors.length === 0, layer: LAYER, rows: result.rows.length, errors }, null, 2));
} catch (error) {
  result.completedAt = new Date().toISOString();
  result.failure = error.stack;
  await fs.writeFile(path.join(OUT, `matrix-${LAYER}.json`), JSON.stringify(result, null, 2));
  await page.screenshot({ path: path.join(LAYER_OUT, "failure.png"), fullPage: true }).catch(() => {});
  console.error(error.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
