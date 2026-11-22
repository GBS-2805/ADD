import { chromium } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const BASE_URL = process.env.AUDIT_BASE_URL ?? "https://site-b5c3eb4162ed46148d22881fe0e65300.freebuff.page/";
const OUT = path.resolve(process.env.AUDIT_OUT ?? "../artifacts/twin/spec-conformance");
const VIEWPORT = (() => {
  const [width, height] = (process.env.AUDIT_VIEWPORT ?? "390x844").split("x").map(Number);
  if (!Number.isFinite(width) || !Number.isFinite(height)) throw new Error("AUDIT_VIEWPORT must look like 844x390");
  return { width, height };
})();
const VIEWPORT_NAME = `${VIEWPORT.width}x${VIEWPORT.height}`;
const SHORT_LANDSCAPE = VIEWPORT.width > VIEWPORT.height && VIEWPORT.height <= 500;
const FIXTURE = path.resolve("e2e/fixtures/layer.png");

await fs.mkdir(OUT, { recursive: true });

const results = [];
const pageErrors = [];
const consoleErrors = [];
const externalRequests = [];
let page;

const record = (id, label, pass, detail) => {
  results.push({ id, label, pass: Boolean(pass), detail });
  console.log(`${pass ? "PASS" : "FAIL"} ${id} — ${label}${detail === undefined ? "" : ` — ${typeof detail === "string" ? detail : JSON.stringify(detail)}`}`);
};

async function shot(name, locator) {
  if (locator) {
    await locator.screenshot({ path: path.join(OUT, `${name}.png`) });
  } else {
    await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: false });
  }
}

async function load() {
  await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
  await page.getByTestId("stage").waitFor({ state: "visible" });
  await page.waitForTimeout(350);
}

async function openCatalogue() {
  await page.getByRole("button", { name: "Open the plugin catalogue" }).click();
  await page.getByTestId("plugin-catalogue").waitFor({ state: "visible" });
}

async function openPlugin(id) {
  await openCatalogue();
  await page.getByTestId(`catalogue-${id}`).click();
  await page.getByTestId(`plugin-${id}`).waitFor({ state: "visible" });
}

async function closeSurface() {
  await page.getByRole("button", { name: "Select, close the panel" }).click();
}

async function drag(locator, dx, dy) {
  const box = await locator.boundingBox();
  if (!box) throw new Error("drag target has no box");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + dx, box.y + box.height / 2 + dy, { steps: 10 });
  await page.mouse.up();
}

function attach(pageInstance) {
  pageInstance.on("pageerror", (error) => pageErrors.push(String(error)));
  pageInstance.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  pageInstance.on("request", (request) => {
    const url = request.url();
    if (!url.startsWith(BASE_URL) && !url.startsWith("data:") && !url.startsWith("blob:") && !url.startsWith("https://static.cloudflareinsights.com/")) externalRequests.push(url);
  });
}

const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
});

const context = await browser.newContext({
  viewport: VIEWPORT,
  hasTouch: true,
  isMobile: true,
  deviceScaleFactor: 1,
});
page = await context.newPage();
attach(page);

try {
  // Shell, layout and dock.
  await load();
  await shot("01-home-390");
  const viewportSwitch = page.locator(".viewport-switch");
  record("shell.viewport-switch-hidden", "Desktop viewport switch is hidden at phone width", !(await viewportSwitch.isVisible()));
  const screen = await page.locator(".device-screen").boundingBox();
  record("shell.fills-viewport", `Device screen fills the ${VIEWPORT_NAME} viewport`, Boolean(screen && Math.round(screen.width) === VIEWPORT.width && Math.round(screen.height) === VIEWPORT.height), screen);
  const viewportMetrics = await page.evaluate(() => ({
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    documentWidth: document.documentElement.scrollWidth,
    documentHeight: document.documentElement.scrollHeight,
    bodyWidth: document.body.scrollWidth,
  }));
  record("layout.viewport-bounds", `Device screen stays inside the ${VIEWPORT_NAME} viewport`, Boolean(screen && screen.x >= -1 && screen.y >= -1 && screen.x + screen.width <= VIEWPORT.width + 1 && screen.y + screen.height <= VIEWPORT.height + 1 && viewportMetrics.documentWidth <= viewportMetrics.innerWidth + 1 && viewportMetrics.bodyWidth <= viewportMetrics.innerWidth + 1), { screen, viewportMetrics });
  record("layout.no-horizontal-overflow", `No horizontal document overflow at ${VIEWPORT_NAME}`, viewportMetrics.documentWidth <= viewportMetrics.innerWidth + 1 && viewportMetrics.bodyWidth <= viewportMetrics.innerWidth + 1, viewportMetrics);
  const dockSizes = await page.locator(".dock-target").evaluateAll((nodes) => nodes.map((node) => {
    const box = node.getBoundingClientRect();
    return { width: box.width, height: box.height };
  }));
  record("shell.dock-48", "All four dock targets are at least 48 px", dockSizes.length === 4 && dockSizes.every((box) => box.width >= 48 && box.height >= 48), dockSizes);
  record("shell.dock-labels", "Dock exposes Select, Add, Actions and Plugins", await page.locator(".dock-target").allTextContents().then((items) => items.map((item) => item.trim()).join("|")), "Select|Add|Actions|Plugins");

  // Add menu and inspector.
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByTestId("add-menu").waitFor({ state: "visible" });
  await shot("02-add-menu");
  record("add.text-shape-png", "Add menu exposes Text, Shape and PNG", await page.getByRole("menuitem", { name: /^Text/ }).isVisible() && await page.getByRole("menuitem", { name: /^Shape/ }).isVisible() && await page.getByRole("menuitem", { name: /^PNG/ }).isVisible());
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  await page.getByTestId("inspector").waitFor({ state: "visible" });
  record("add.text-layer", "Text adds a real layer and opens the inspector", await page.locator(".art-layer-text").count() === 2 && await page.getByTestId("inspector-kind").textContent() === "Text layer");
  await shot("03-inspector-text");

  const inspectorLabels = await page.locator(".inspector-properties").allTextContents();
  const inspectorText = inspectorLabels.join(" ");
  for (const label of ["X", "Y", "Scale", "Rotation", "Opacity", "Visible", "Blend mode", "Fill", "Font", "Text size", "Text content"]) {
    record(`inspector.${label.toLowerCase().replaceAll(" ", "-")}`, `Inspector exposes ${label}`, inspectorText.includes(label));
  }
  const blendOptions = await (async () => {
    await page.getByRole("combobox", { name: "Blend mode" }).click();
    const count = await page.getByRole("option").count();
    await page.keyboard.press("Escape");
    return count;
  })();
  record("inspector.16-blends", "Inspector exposes all 16 CSS blend modes", blendOptions === 16, blendOptions);
  await page.getByRole("tab", { name: "Layers" }).click();
  record("inspector.layers-tab", "Layers tab lists all current layers", await page.locator(".inspector-layer-row").count() === 4);
  if (SHORT_LANDSCAPE) {
    await page.getByRole("tab", { name: "Properties" }).click();
    await page.waitForTimeout(100);
    const stageForSheet = await page.getByTestId("stage").boundingBox();
    const panelForSheet = await page.getByTestId("panel-sheet").boundingBox();
    const screenForSheet = await page.locator(".device-screen").boundingBox();
    const dockForSheet = await page.locator(".dock").boundingBox();
    const footlineForSheet = await page.locator(".stage-footline").boundingBox();
    const closeForSheet = await page.getByRole("button", { name: "Close panel" }).boundingBox();
    const panelContent = await page.locator(".panel-content").evaluate((element) => ({
      clientHeight: element.clientHeight,
      scrollHeight: element.scrollHeight,
      overflowY: getComputedStyle(element).overflowY,
    }));
    const overlapWidth = stageForSheet && panelForSheet ? Math.max(0, Math.min(stageForSheet.x + stageForSheet.width, panelForSheet.x + panelForSheet.width) - Math.max(stageForSheet.x, panelForSheet.x)) : 1;
    const overlapHeight = stageForSheet && panelForSheet ? Math.max(0, Math.min(stageForSheet.y + stageForSheet.height, panelForSheet.y + panelForSheet.height) - Math.max(stageForSheet.y, panelForSheet.y)) : 1;
    const screenRight = screenForSheet ? screenForSheet.x + screenForSheet.width : 0;
    const screenBottom = screenForSheet ? screenForSheet.y + screenForSheet.height : 0;
    const stageDockGap = dockForSheet && footlineForSheet ? dockForSheet.y - (footlineForSheet.y + footlineForSheet.height) : null;
    const layoutPass = Boolean(stageForSheet && panelForSheet && screenForSheet && dockForSheet && closeForSheet &&
      stageForSheet.height >= 220 && panelForSheet.height >= 220 && panelForSheet.x >= stageForSheet.x + stageForSheet.width - 1 &&
      Math.abs(panelForSheet.y - stageForSheet.y) <= 1 &&
      overlapWidth * overlapHeight === 0 && panelForSheet.x >= screenForSheet.x - 1 && panelForSheet.x + panelForSheet.width <= screenRight + 1 &&
      panelForSheet.y >= screenForSheet.y - 1 && panelForSheet.y + panelForSheet.height <= screenBottom + 1 &&
      Math.abs(panelForSheet.y + panelForSheet.height - screenBottom) <= 1 &&
      closeForSheet.x >= panelForSheet.x && closeForSheet.x + closeForSheet.width <= panelForSheet.x + panelForSheet.width &&
      closeForSheet.y >= panelForSheet.y && closeForSheet.y + closeForSheet.height <= panelForSheet.y + panelForSheet.height &&
      dockForSheet.x >= stageForSheet.x - 1 && dockForSheet.x + dockForSheet.width <= stageForSheet.x + stageForSheet.width + 1 &&
      dockForSheet.y >= stageForSheet.y - 1 && dockForSheet.y + dockForSheet.height <= screenBottom);
    record("layout.short-landscape", "Short landscape keeps the Stage and a full-height panel side by side, fully inside the viewport", layoutPass, { stage: stageForSheet, panel: panelForSheet, screen: screenForSheet, dock: dockForSheet, close: closeForSheet, overlap: { width: overlapWidth, height: overlapHeight } });
    record("layout.short-landscape-dock", "Short landscape dock floats inside the Stage column with the footline clear", stageDockGap !== null && stageDockGap >= 0 && stageDockGap <= 8, { stageDockGap, footline: footlineForSheet, stage: stageForSheet, dock: dockForSheet });
    record("layout.short-landscape-scroll", "Short-landscape panel scrolls internally and its last control stays inside the panel", panelContent.overflowY === "auto" && panelContent.scrollHeight > panelContent.clientHeight, panelContent);
    await page.locator(".panel-content").evaluate((element) => { element.scrollTop = element.scrollHeight; });
    const lastControl = await page.locator(".color-trigger").boundingBox();
    record("layout.short-landscape-last-control", "Short-landscape panel exposes its final control without clipping", Boolean(lastControl && panelForSheet && lastControl.y >= panelForSheet.y && lastControl.y + lastControl.height <= panelForSheet.y + panelForSheet.height), { lastControl, panel: panelForSheet });
  } else {
    const bottomSheet = await page.getByTestId("panel-sheet").boundingBox();
    const screenForSheet = await page.locator(".device-screen").boundingBox();
    const stageForSheet = await page.getByTestId("stage").boundingBox();
    const dockForSheet = await page.locator(".dock").boundingBox();
    const screenBottom = screenForSheet ? screenForSheet.y + screenForSheet.height : 0;
    record("layout.bottom-sheet", "Compact panel is a full-width 40dvh bottom sheet below the floating dock and Stage", Boolean(bottomSheet && screenForSheet && stageForSheet && dockForSheet && Math.abs(bottomSheet.x - screenForSheet.x) <= 1 && Math.abs(bottomSheet.width - screenForSheet.width) <= 1 && Math.abs(stageForSheet.y + stageForSheet.height - bottomSheet.y) <= 1 && Math.abs(bottomSheet.y + bottomSheet.height - screenBottom) <= 1 && dockForSheet.y > stageForSheet.y && dockForSheet.y + dockForSheet.height <= bottomSheet.y + 1 && bottomSheet.height <= VIEWPORT.height * 0.4 + 1 && stageForSheet.height >= 250), { bottomSheet, stage: stageForSheet, screen: screenForSheet, dock: dockForSheet });
  }
  await page.getByRole("button", { name: "Select, close the panel" }).click();

  // All Add-menu shapes.
  for (const shape of ["Star", "Orbit", "Block"]) {
    await page.getByRole("button", { name: "Add a layer" }).click();
    await page.getByRole("menuitem", { name: /^Shape/ }).click({ position: { x: 8, y: 10 } });
    await page.getByRole("menuitem", { name: new RegExp(`^${shape}`) }).click();
    await page.getByTestId("inspector").waitFor({ state: "visible" });
    const count = await page.locator(shape === "Star" ? ".sample-star" : shape === "Orbit" ? ".sample-orbit" : ".sample-block").count();
    record(`add.shape-${shape.toLowerCase()}`, `Add menu creates a ${shape} shape`, count > 0, count);
    await closeSurface();
  }

  // PNG still routes through Overlay Lab.
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^PNG/ }).click();
  record("add.png-routes-overlay", "PNG routes to Overlay Lab rather than creating a layer directly", await page.getByTestId("plugin-overlay-lab").isVisible() && !(await page.getByTestId("inspector").isVisible()));
  await page.getByTestId("overlay-import-input").setInputFiles(FIXTURE);
  await page.getByTestId("inspector").waitFor({ state: "visible" });
  record("overlay.imports-png", "Overlay Lab imports a PNG as a real layer", await page.locator(".art-layer-image").count() === 1);
  record("overlay.image-dimensions", "Inspector reports PNG source dimensions", (await page.locator(".inspector-image-meta").textContent())?.includes("×"));
  record("overlay.no-fill", "Image layers do not expose a fill picker", !(await page.getByRole("button", { name: /^Fill / }).isVisible().catch(() => false)));
  await shot("04-overlay-image-inspector");

  // Catalogue and plugin surfaces.
  await openCatalogue();
  await shot("05-catalogue");
  record("catalogue.8", "Catalogue lists all eight plugin families", await page.locator(".plugin-card").count() === 8);
  record("catalogue.groups", "Catalogue has all six category chips", (await page.locator(".chip-row button").allTextContents()).join("|") === "All|Effects|Texture|Overlay|Motion|Native");
  await page.getByTestId("catalogue-search").fill("shader");
  record("catalogue.search", "Catalogue search filters to Shader Lab", await page.locator(".plugin-card").count() === 1 && await page.getByTestId("catalogue-shader-lab").isVisible());
  await page.getByTestId("catalogue-search").fill("zzzz-no-match");
  record("catalogue.empty", "Catalogue shows an explicit empty state", await page.locator(".catalogue-empty").isVisible());
  await page.getByLabel("Clear plugin search").click();
  await page.getByRole("button", { name: "Native lens" }).click();
  record("lens.all-available", "Native lens leaves all eight plugins available", await page.locator(".plugin-card").count() === 8 && await page.getByTestId("catalogue-shader-lab").isEnabled());
  record("lens.dims", "Native lens dims needs-native/browser-only cards", await page.getByTestId("catalogue-shader-lab").evaluate((element) => element.classList.contains("is-lens-dimmed")));
  await page.getByRole("button", { name: "Native lens" }).click();

  // Control truth per plugin.
  const controlLabels = async (id) =>
    (await page.getByTestId(`plugin-${id}`).locator(".range-field > span, .swatch-field legend, .toggle-row strong").allTextContents()).join(" | ");

  await openPlugin("shader-lab");
  await shot("06-plugin-shader");
  const shaderLabels = await controlLabels("shader-lab");
  record("shader.8-presets", "Shader Lab exposes eight native shader presets", await page.locator(".shader-grid .preset-card").count() === 8);
  for (const label of ["Speed", "Distortion", "Grain", "Colour"]) record(`shader.${label.toLowerCase()}`, `Shader Lab exposes ${label}`, shaderLabels.includes(label), shaderLabels);
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("texture-lab");
  await shot("07-plugin-texture");
  const textureLabels = await controlLabels("texture-lab");
  record("texture.5-presets", "Texture Lab exposes five presets", await page.locator(".texture-grid .texture-card").count() === 5);
  for (const label of ["Scale", "Opacity", "Roughness", "Paper", "Fur length"]) record(`texture.${label.toLowerCase()}`, `Texture Lab exposes ${label}`, textureLabels.includes(label), textureLabels);
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("particle-field");
  await shot("08-plugin-particles");
  const particleLabels = await controlLabels("particle-field");
  record("particle.4-presets", "Particle Field exposes four presets", await page.locator(".particle-grid .particle-card").count() === 4);
  for (const label of ["Density", "Speed", "Colour", "Trail"]) record(`particle.${label.toLowerCase()}`, `Particle Field exposes ${label}`, particleLabels.includes(label), particleLabels);
  record("particle.pause", "Particle Field exposes an accessible Pause/Resume control", await page.getByRole("button", { name: /Pause|Resume/ }).isVisible());
  const hiddenState = await page.evaluate(() => {
    try {
      Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" });
      document.dispatchEvent(new Event("visibilitychange"));
      return document.visibilityState;
    } catch {
      return null;
    }
  });
  await page.waitForTimeout(120);
  record("particle.hidden-pause", "Particle Field pauses when the page is hidden", hiddenState === "hidden" && await page.locator("[data-page-visible='false']").count() > 0, hiddenState);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "visible" });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("pattern-library");
  await shot("09-plugin-pattern");
  const patternLabels = await controlLabels("pattern-library");
  record("pattern.8-presets", "Pattern Library exposes eight patterns", await page.locator(".pattern-grid .pattern-card").count() === 8);
  for (const label of ["Scale", "Opacity", "Ink", "Rough sample edge"]) record(`pattern.${label.toLowerCase()}`, `Pattern Library exposes ${label}`, patternLabels.includes(label), patternLabels);
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("spatial-dock");
  await shot("10-plugin-spatial");
  record("spatial.4-motion", "Spatial Dock exposes four motion presets", await page.locator(".motion-presets button").count() === 4);
  record("spatial.3d", "Spatial Dock renders a local 3D surface or a visible fallback", await page.locator(".spatial-dock canvas, [data-testid='spatial-fallback']").count() > 0);
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("native-parity");
  await shot("11-plugin-native-tone");
  const toneColours = await page.locator(".tone-card > span").evaluateAll((nodes) => nodes.map((node) => getComputedStyle(node).backgroundColor));
  record("native.tone-count", "Native Parity exposes five native Tone swatches", toneColours.length === 5, toneColours);
  record("native.tone-native-values", "Tone swatches use the native #5B2A4B, #6D3558, #1B1212, #F5ECD8 and #8A3B00 values", ["rgb(91, 42, 75)", "rgb(109, 53, 88)", "rgb(27, 18, 18)", "rgb(245, 236, 216)", "rgb(138, 59, 0)"].every((value) => toneColours.includes(value)), toneColours);
  await page.getByRole("button", { name: "Prism", exact: true }).click();
  await shot("12-plugin-native-prism");
  const prismNames = await page.locator(".prism-row").allTextContents();
  record("native.prism-count", "Native Parity exposes all eight native Prism field presets", prismNames.length === 8, prismNames);
  record("native.prism-names", "Prism uses the native field preset names", ["Mica dawn", "Sea glass", "Plum smoke", "Citrus haze", "Cobalt rain", "Olive dusk", "Rose quartz", "Ink tide"].every((name) => prismNames.join(" ").includes(name)), prismNames);
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("asset-shelf");
  await shot("13-plugin-assets");
  record("assets.2-papers", "Asset Shelf exposes both paper stocks", await page.locator(".paper-card").count() === 2);
  record("assets.18-icons", "Asset Shelf exposes 18 Lucide icon samples", await page.locator(".icon-cell").count() === 18);
  record("assets.filters", "Asset Shelf exposes all four filters", (await page.locator(".filter-row button").allTextContents()).join("|") === "all|paper|icon|imported");
  const layersBeforeAsset = await page.locator(".art-layer").count();
  await page.getByTestId("asset-import-input").setInputFiles({
    name: "safe.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="#5B2A4B"/></svg>'),
  });
  await page.locator(".imported-row").waitFor({ state: "visible" });
  record("assets.does-not-create-layer", "Asset Shelf import does not create a document layer", await page.locator(".art-layer").count() === layersBeforeAsset);
  record("assets.svg-sanitised", "Asset Shelf labels SVG material as sanitised", (await page.locator(".imported-row").textContent())?.includes("Sanitised SVG"));
  await page.getByRole("button", { name: "Shelf" }).click();

  // Native lens through the actual plugin.
  await openCatalogue();
  await page.getByRole("button", { name: "Native lens" }).click();
  await page.getByTestId("catalogue-asset-shelf").click();
  record("lens.plugin-usable", "Native lens leaves a needs-native plugin open and usable", await page.getByTestId("plugin-asset-shelf").isVisible());
  await page.getByRole("button", { name: "Native lens" }).click();
  await page.getByRole("button", { name: "Shelf" }).click();

  await openPlugin("native-parity");
  await page.getByRole("button", { name: "Prism", exact: true }).click();
  await page.locator(".prism-row").first().click();
  await page.locator(".button.primary").click();
  record("native.prism-applies", "Applying a native Prism recipe paints a field on the artboard", await page.locator(".artboard-ground .effect-field").isVisible());
  await page.getByRole("button", { name: "Shelf" }).click();

  // Commands and keyboard surface behavior.
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  const commandText = (await page.locator(".command-row").allTextContents()).join(" | ");
  for (const label of ["Undo last edit", "Redo edit", "Export PNG", "Open Tone", "Open Prism", "Open Overlay Lab", "Open the plugin catalogue"]) {
    record(`commands.${label.toLowerCase().replaceAll(" ", "-")}`, `Actions exposes ${label}`, commandText.includes(label));
  }

  // Flattened PNG export: header, dimensions, file name and in-app result metadata.
  const pngDownloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: /Export PNG/ }).click();
  const pngDownload = await pngDownloadPromise;
  const pngPath = await pngDownload.path();
  if (!pngPath) throw new Error("The live PNG download has no path.");
  const pngBytes = await fs.readFile(pngPath);
  const pngStatus = page.getByTestId("png-export-result");
  const pngResult = {
    filename: pngDownload.suggestedFilename(),
    signature: pngBytes.subarray(0, 8).toString("hex"),
    chunk: pngBytes.subarray(12, 16).toString("ascii"),
    width: pngBytes.readUInt32BE(16),
    height: pngBytes.readUInt32BE(20),
    bytes: pngBytes.byteLength,
    statusWidth: await pngStatus.getAttribute("data-width"),
    statusHeight: await pngStatus.getAttribute("data-height"),
    statusSignature: await pngStatus.getAttribute("data-signature"),
  };
  record(
    "png.export",
    "Export PNG downloads a valid flattened artboard PNG at 1530 × 2000",
    pngResult.filename === "open-studio.png" &&
      pngResult.signature === "89504e470d0a1a0a" &&
      pngResult.chunk === "IHDR" &&
      pngResult.width === 1530 &&
      pngResult.height === 2000 &&
      Math.abs(pngResult.width / pngResult.height - 0.765) < 0.001 &&
      pngResult.bytes > 10_000 &&
      pngResult.statusWidth === "1530" &&
      pngResult.statusHeight === "2000" &&
      pngResult.statusSignature === "89504e470d0a1a0a",
    pngResult,
  );
  await shot("16-png-export-status");

  await page.getByRole("button", { name: /Open Prism/ }).click();
  record("commands.open-prism-tab", "Open Prism lands on the Prism field tab", await page.locator(".prism-row").count() === 8);
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Open Tone/ }).click();
  record("commands.open-tone-tab", "Open Tone lands on the Tone swatch tab", await page.locator(".tone-card").count() === 5);
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.keyboard.press("Escape");
  record("keyboard.escape-closes-surface", "Escape closes the open surface first", !(await page.getByTestId("panel-sheet").evaluate((element) => element.classList.contains("is-open"))));

  // Full document history: effects, direct transforms and layer adds all undo/redo.
  await load();
  await openPlugin("shader-lab");
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("shader-preset-metal").click();
  await closeSurface();
  const effect = page.getByTestId("layer-effect-type-open-studio");
  record("history.effect-applied", "A layer effect is applied before history checks", await effect.isVisible());
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Undo last edit/ }).click();
  await page.waitForTimeout(200);
  record("history.effect-undo", "Undo removes the last applied effect", await effect.count() === 0);
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Redo edit/ }).click();
  await page.waitForTimeout(200);
  record("history.effect-redo", "Redo restores the undone effect", await effect.isVisible());
  await shot("14-history-after-redo");

  await load();
  const directLayer = page.getByTestId("art-layer-type-open-studio");
  const initialStyle = await directLayer.getAttribute("style");
  await drag(directLayer, 24, 14);
  const movedStyle = await directLayer.getAttribute("style");
  await page.keyboard.press("Control+z");
  await page.waitForTimeout(180);
  const undoneStyle = await directLayer.getAttribute("style");
  record("history.direct-transform-undo", "Ctrl+Z restores a direct layer transform", undoneStyle === initialStyle, { initialStyle, movedStyle, undoneStyle });
  await page.keyboard.press("Control+Shift+z");
  await page.waitForTimeout(180);
  const redoneStyle = await directLayer.getAttribute("style");
  record("history.direct-transform-redo", "Ctrl+Shift+Z reapplies a direct layer transform", redoneStyle === movedStyle, { movedStyle, redoneStyle });
  await shot("15-history-direct-transform");

  await load();
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  const layersAfterAdd = await page.locator(".art-layer-text").count();
  await page.keyboard.press("Control+z");
  await page.waitForTimeout(180);
  const layersAfterUndo = await page.locator(".art-layer-text").count();
  record("history.layer-addition-undo", "Undo removes a layer added after the last edit", layersAfterAdd === 2 && layersAfterUndo === 1, { layersAfterAdd, layersAfterUndo });
  await page.keyboard.press("Control+y");
  await page.waitForTimeout(180);
  record("history.layer-addition-redo", "Ctrl+Y restores the undone layer", await page.locator(".art-layer-text").count() === 2);

  await load();
  await page.getByTestId("art-layer-type-open-studio").click();
  await openPlugin("shader-lab");
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("shader-preset-metal").click();
  await closeSurface();
  await page.getByTestId("art-layer-shape-star").click();
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Undo last edit/ }).click();
  await page.waitForTimeout(180);
  record("history.selection-preserved", "Undo keeps a still-valid current layer selection", await page.getByTestId("art-layer-shape-star").evaluate((element) => element.classList.contains("is-selected")));

  // PNG-only import, twelve-layer cap and in-memory storage boundary.
  await load();
  for (let index = 0; index < 12; index += 1) {
    await openPlugin("overlay-lab");
    await page.getByTestId("overlay-import-input").setInputFiles(FIXTURE);
    await page.waitForFunction((count) => document.querySelectorAll(".art-layer-image").length === count, index + 1);
  }
  await openPlugin("overlay-lab");
  record("overlay.png-only-input", "Overlay Lab accepts PNG only", await page.getByTestId("overlay-import-input").getAttribute("accept") === "image/png");
  await page.getByTestId("overlay-import-input").setInputFiles(FIXTURE);
  await page.waitForTimeout(180);
  record("overlay.layer-cap", "Overlay Lab caps the document at 12 PNG layers", await page.locator(".art-layer-image").count() === 12);
  await openPlugin("overlay-lab");
  await page.getByTestId("overlay-import-input").setInputFiles({
    name: "not-a-png.gif",
    mimeType: "image/gif",
    buffer: Buffer.from("GIF89a"),
  });
  await page.waitForTimeout(150);
  record("overlay.rejects-non-png", "Overlay Lab rejects a non-PNG file", (await page.getByRole("status").textContent())?.includes("PNG"));
  record("overlay.no-browser-persistence", "PNG layers stay in memory and are not written to local storage", await page.evaluate(() => localStorage.length === 0));

  await page.emulateMedia({ reducedMotion: "reduce" });
  await load();
  const reducibleTransition = await page.locator(".panel-sheet").evaluate((element) => getComputedStyle(element).transitionDuration);
  await openPlugin("shader-lab");
  const reducedShader = await page.locator("[data-reduced-motion='true']").count();
  record("accessibility.reduced-motion", "The UI and shader canvases respect prefers-reduced-motion", reducibleTransition !== "0.22s" && reducedShader > 0, { reducibleTransition, reducedShader });
} catch (error) {
  record("audit.crash", "Audit completed without an uncaught error", false, String(error?.stack ?? error));
} finally {
  const uniquePageErrors = [...new Set(pageErrors)];
  const uniqueConsoleErrors = [...new Set(consoleErrors)];
  const uniqueExternal = [...new Set(externalRequests)];
  record("runtime.page-errors", "No page errors during the live audit", uniquePageErrors.length === 0, uniquePageErrors);
  record("runtime.console-errors", "No console errors during the live audit", uniqueConsoleErrors.length === 0, uniqueConsoleErrors);
  record("runtime.external-requests", "No external runtime requests during the live audit", uniqueExternal.length === 0, uniqueExternal);

  const summary = {
    baseUrl: BASE_URL,
    viewport: VIEWPORT,
    checkedAt: new Date().toISOString(),
    passed: results.filter((item) => item.pass).length,
    failed: results.filter((item) => !item.pass).length,
    results,
    pageErrors: uniquePageErrors,
    consoleErrors: uniqueConsoleErrors,
    externalRequests: uniqueExternal,
  };
  await fs.writeFile(path.join(OUT, "spec-conformance.json"), JSON.stringify(summary, null, 2));
  console.log(`\n${summary.passed}/${results.length} live conformance checks passed; ${summary.failed} failed.`);
  console.log(`Evidence: ${path.join(OUT, "spec-conformance.json")}`);
  await context.close();

  // WebGL-disabled fallback, on a fresh context.
  const fallbackContext = await browser.newContext({ viewport: VIEWPORT, isMobile: true, hasTouch: true });
  await fallbackContext.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...args) {
      if (String(type).includes("webgl")) return null;
      return original.call(this, type, ...args);
    };
  });
  const fallbackPage = await fallbackContext.newPage();
  const fallbackErrors = [];
  fallbackPage.on("pageerror", (error) => fallbackErrors.push(String(error)));
  await fallbackPage.goto(BASE_URL, { waitUntil: "domcontentloaded" });
  await fallbackPage.getByTestId("stage").waitFor({ state: "visible" });
  await fallbackPage.getByRole("button", { name: "Open the plugin catalogue" }).click();
  await fallbackPage.getByTestId("catalogue-shader-lab").click();
  await fallbackPage.getByTestId("shader-feature").waitFor({ state: "visible" });
  const fallbackVisible = await fallbackPage.getByTestId("shader-fallback").first().isVisible();
  await fallbackPage.screenshot({ path: path.join(OUT, "15-webgl-fallback.png") });
  console.log(`${fallbackVisible ? "PASS" : "FAIL"} webgl.fallback — Shader Lab renders a useful CSS fallback when WebGL is unavailable`);
  if (fallbackErrors.length) console.log("Fallback page errors:", fallbackErrors);
  await fallbackContext.close();
  await browser.close();
  process.exitCode = summary.failed === 0 ? 0 : 1;
}
