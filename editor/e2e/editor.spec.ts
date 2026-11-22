import { expect, test } from "@playwright/test";

const PNG_1X1 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

async function openWorkbench(page: import("@playwright/test").Page, width = 1440, height = 900) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

test("layersRename", async ({ page }) => {
  await openWorkbench(page);
  const row = page.locator('[data-layer-row="shape-star"]');
  await row.getByText("Shape / Star", { exact: true }).dblclick();
  const input = page.getByTestId("layer-rename-input");
  await input.fill("Star renamed");
  await input.press("Enter");
  await expect(row.getByText("Star renamed", { exact: true })).toBeVisible();
});

test("scrollsSelectedLayerIntoView", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("art-layer-shape-star").click({ force: true });
  const row = page.locator('[data-layer-row="shape-star"]');
  await expect(row).toHaveAttribute("aria-selected", "true");
  await expect(row).toBeInViewport();
  const focusedRow = await page.evaluate(() => document.activeElement?.getAttribute("data-layer-row") ?? null);
  expect(focusedRow).toBeNull();
});

test("emptyDocumentShowsHint", async ({ page }) => {
  await openWorkbench(page);
  for (let index = 0; index < 3; index += 1) {
    await page.locator("[data-layer-row]").first().focus();
    await page.keyboard.press("Delete");
  }
  await expect(page.getByText("No layers. Add text, a shape or a PNG.")).toBeVisible();
  await expect(page.locator("[data-layer-row]")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Open document menu" })).toBeEnabled();
  await expect(page.getByTestId("editor-shell")).toBeVisible();
});

test("imageSurvivesRemoveUndo", async ({ page }) => {
  await openWorkbench(page);
  await page.locator('[data-testid="editor-image-input"]').setInputFiles({
    name: "dot.png",
    mimeType: "image/png",
    buffer: Buffer.from(PNG_1X1, "base64"),
  });
  const pngRow = page.locator('[data-layer-row^="png-"]');
  await expect(pngRow).toHaveCount(1);
  const pngId = await pngRow.getAttribute("data-layer-row");
  await pngRow.focus();
  await page.keyboard.press("Delete");
  await expect(pngRow).toHaveCount(0);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(pngRow).toHaveCount(1);
  await expect(page.locator(`[data-layer-id="${pngId}"]`)).toBeAttached();
});

test("touchReorderWorksAt360", async ({ page }) => {
  await openWorkbench(page, 360, 840);
  await page.locator('[data-drawer-trigger="layers"]').first().click();
  await expect(page.getByTestId("bottom-sheet-layers")).toBeVisible();
  const from = page.locator('[data-layer-row="type-open-studio"]');
  const target = page.locator('[data-layer-row="shape-orbit"]');
  const grip = from.getByRole("button", { name: "Reorder Type / OPEN STUDIO" });
  const fromBox = await grip.boundingBox();
  const targetBox = await target.boundingBox();
  if (!fromBox || !targetBox) throw new Error("The touch reorder rows need layout boxes.");

  const fromX = fromBox.x + fromBox.width / 2;
  const fromY = fromBox.y + fromBox.height / 2;
  const targetX = targetBox.x + targetBox.width / 2;
  const targetY = targetBox.y + targetBox.height / 2;
  await grip.dispatchEvent("pointerdown", {
    pointerId: 11,
    pointerType: "touch",
    isPrimary: true,
    button: 0,
    buttons: 1,
    clientX: fromX,
    clientY: fromY,
  });
  await grip.dispatchEvent("pointermove", {
    pointerId: 11,
    pointerType: "touch",
    isPrimary: true,
    buttons: 1,
    clientX: targetX,
    clientY: targetY,
  });
  await grip.dispatchEvent("pointerup", {
    pointerId: 11,
    pointerType: "touch",
    isPrimary: true,
    button: 0,
    buttons: 0,
    clientX: targetX,
    clientY: targetY,
  });

  await expect(page.locator("[data-layer-row]").first()).toHaveAttribute("data-layer-row", "type-open-studio");
});

/* Task 4: keyboard, palette, effects, preview, print and device modes. */

const canvasRegion = (page: import("@playwright/test").Page) =>
  page.getByRole("region", { name: "Artboard canvas" });

const layerLeft = async (page: import("@playwright/test").Page, id: string) =>
  page.locator(`[data-testid="art-layer-${id}"]`).evaluate((element) => parseFloat((element as HTMLElement).style.left));

test("cmdKOpensPalette", async ({ page }) => {
  await openWorkbench(page);
  await canvasRegion(page).focus();
  await page.keyboard.press("Control+k");
  await expect(page.getByTestId("command-palette")).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Search actions" })).toBeFocused();
});

test("paletteRunsUndo", async ({ page }) => {
  await openWorkbench(page);
  const layer = page.getByTestId("art-layer-type-open-studio");
  const before = await layerLeft(page, "type-open-studio");
  await canvasRegion(page).focus();
  await page.keyboard.press("ArrowRight");
  await expect.poll(() => layerLeft(page, "type-open-studio")).toBeGreaterThan(before);
  await page.keyboard.press("Control+k");
  await page.locator('[data-command-id="undo"]').click();
  await expect.poll(() => layerLeft(page, "type-open-studio")).toBeCloseTo(before, 1);
  await expect(layer).toBeVisible();
});

test("arrowNudgesSelection", async ({ page }) => {
  await openWorkbench(page);
  const before = await layerLeft(page, "type-open-studio");
  await canvasRegion(page).focus();
  await page.keyboard.press("ArrowRight");
  await expect.poll(() => layerLeft(page, "type-open-studio")).toBeGreaterThan(before);
  const after = await layerLeft(page, "type-open-studio");
  expect(after - before).toBeGreaterThanOrEqual(0.9);
  expect(after - before).toBeLessThanOrEqual(1.1);
});

test("deleteRemovesSelection", async ({ page }) => {
  await openWorkbench(page);
  await expect(page.locator("[data-layer-row]")).toHaveCount(3);
  await canvasRegion(page).focus();
  await page.keyboard.press("Delete");
  await expect(page.locator("[data-layer-row]")).toHaveCount(2);
  await expect(page.getByTestId("art-layer-type-open-studio")).toHaveCount(0);
});

test("shortcutsDoNotFireInInspectorText", async ({ page }) => {
  await openWorkbench(page);
  const textarea = page.locator("textarea").first();
  await textarea.focus();
  await page.keyboard.press("h");
  await expect(textarea).toBeFocused();
  await expect(page.getByRole("button", { name: "Move tool" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("button", { name: "Hand tool" })).toHaveAttribute("aria-pressed", "false");
});

test("shortcutsDoNotFireInMenu", async ({ page }) => {
  await openWorkbench(page);
  await page.getByRole("button", { name: "Open document menu" }).click();
  const menu = page.getByRole("menu");
  await expect(menu).toBeVisible();
  await page.keyboard.press("h");
  await expect(menu).toBeVisible();
  await expect(page.getByRole("button", { name: "Move tool" })).toHaveAttribute("aria-pressed", "true");
  await page.keyboard.press("Escape");
});

test("focusReturnsAfterPalette", async ({ page }) => {
  await openWorkbench(page);
  const canvas = canvasRegion(page);
  await canvas.focus();
  await page.keyboard.press("Control+k");
  await expect(page.getByRole("textbox", { name: "Search actions" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("command-palette")).toHaveCount(0);
  await expect(canvas).toBeFocused();
});

test("escapeClosesPreviewBeforeSelection", async ({ page }) => {
  await openWorkbench(page);
  await expect(page.getByTestId("art-layer-type-open-studio")).toHaveClass(/is-selected/);
  await page.getByTestId("preview-trigger").click();
  await expect(page.getByTestId("preview-mode")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("preview-mode")).toHaveCount(0);
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expect(page.getByTestId("art-layer-type-open-studio")).toHaveClass(/is-selected/);
});

test("opensEffectsFromToolbar", async ({ page }) => {
  await openWorkbench(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await expect(page.getByTestId("effects-panel")).toBeVisible();
  await expect(page.getByTestId("plugin-catalogue")).toBeVisible();
  await expect(page.getByTestId("catalogue-shader-lab")).toBeVisible();
});

test("appliesShaderToArtboard", async ({ page }) => {
  await openWorkbench(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await page.getByTestId("catalogue-shader-lab").click();
  // Check the panel before the preset switch: recompiling the shader under the CI
  // software renderer is slow, and this assertion must not compete with it for budget.
  await expect(page.getByTestId("plugin-shader-lab")).toBeVisible();
  const ground = page.locator('[data-testid="editor-artboard"] .artboard-ground');
  await expect(ground).toHaveAttribute("data-effect", "shader");
  await expect(ground).toHaveAttribute("data-effect-preset", "Paper stock");
  await page.getByTestId("shader-preset-paper").click();
  // The toast is transient, and React can replay the apply updater, which swaps in the
  // "already on the artboard" message. Assert the document's effect state instead.
  await expect(ground).toHaveAttribute("data-effect-preset", "Paper texture", { timeout: 15_000 });
});

test("appliesOverlayTextureToLayer", async ({ page }) => {
  await openWorkbench(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await page.getByTestId("catalogue-texture-lab").click();
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("texture-preset-paper").click();
  const clip = page.locator('[data-testid="editor-artboard"] [data-testid="layer-effect-clip"]');
  await expect(clip).toHaveCount(1);
  await expect(clip).toHaveAttribute("data-effect", "texture");
  await expect(clip).toHaveAttribute("data-effect-preset", "PaperTexture");
});

test("multiSelectionBlocksLayerEffect", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("art-layer-shape-star").click({ modifiers: ["Shift"] });
  await expect(page.locator('[data-layer-row][aria-selected="true"]')).toHaveCount(2);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await page.getByTestId("catalogue-texture-lab").click();
  await expect(page.getByRole("button", { name: "Layer", exact: true })).toBeDisabled();
  await expect(page.getByText("Select one layer for a layer effect.")).toBeVisible();
});

test("pluginSessionSurvivesTabSwitch", async ({ page }) => {
  await openWorkbench(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await page.getByTestId("catalogue-shader-lab").click();
  const speed = page.getByRole("slider", { name: /Speed/ });
  await speed.fill("0.8");
  await page.getByRole("tab", { name: "Design" }).click();
  await expect(page.getByTestId("plugin-shader-lab")).not.toBeVisible();
  await page.getByRole("tab", { name: "Effects" }).click();
  await expect(page.getByTestId("plugin-shader-lab")).toBeVisible();
  await expect(page.getByRole("slider", { name: /Speed/ })).toHaveValue("0.8");
});

test("previewModeIsReadOnly", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("preview-trigger").click();
  await expect(page.getByTestId("preview-mode")).toBeVisible();
  await expect(page.getByTestId("preview-artboard")).toHaveAttribute("data-artboard", "readonly");
  await expect(page.locator('[data-testid="preview-mode"] [data-handle]')).toHaveCount(0);
  await expect(page.locator('[data-testid="preview-mode"] [data-testid="selection-overlay"]')).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Save project/ })).toHaveCount(0);
  await expect(page.getByTestId("editor-shell")).toHaveAttribute("aria-hidden", "true");
});

test("previewSwitches411And840", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("preview-trigger").click();
  await expect(page.getByTestId("device-frame-compact")).toBeVisible();
  await page.getByText("Expanded 840", { exact: true }).click();
  await expect(page.getByTestId("device-frame-expanded")).toBeVisible();
  await expect(page.getByTestId("preview-mode")).toHaveAttribute("data-layout", "expanded");
  await page.getByText("Compact 411", { exact: true }).click();
  await expect(page.getByTestId("device-frame-compact")).toBeVisible();
});

test("previewExitRestoresEditor", async ({ page }) => {
  await openWorkbench(page);
  const trigger = page.getByTestId("preview-trigger");
  await trigger.click();
  await page.getByRole("button", { name: "Exit preview" }).click();
  await expect(page.getByTestId("preview-mode")).toHaveCount(0);
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expect(trigger).toBeFocused();
});

test("coverQuickModeAt349", async ({ page }) => {
  await openWorkbench(page, 349, 316);
  await expect(page.getByTestId("editor-shell")).toHaveAttribute("data-mode", "cover");
  await expect(page.getByTestId("cover-shell")).toBeVisible();
  const buttons = page.getByTestId("cover-quick-bar").locator("button");
  await expect(buttons).toHaveCount(5);
  for (const label of ["Move / Hand", "Add layer", "Layers", "Undo", "Preview"]) {
    await expect(page.getByTestId("cover-quick-bar").getByRole("button", { name: label, exact: true })).toBeVisible();
  }
  for (let index = 0; index < 5; index += 1) {
    const box = await buttons.nth(index).boundingBox();
    if (!box) throw new Error("Cover action has no layout box.");
    expect(box.width).toBeGreaterThanOrEqual(44);
    expect(box.height).toBeGreaterThanOrEqual(44);
  }
});

test("coverDoesNotMountInspector", async ({ page }) => {
  await openWorkbench(page, 320, 320);
  await expect(page.getByTestId("cover-shell")).toBeVisible();
  await expect(page.getByTestId("inspector-panel")).toHaveCount(0);
  await expect(page.getByTestId("editor-splitter")).toHaveCount(0);
  await expect(page.getByTestId("ruler-x")).toHaveCount(0);
  await page.keyboard.press("Control+k");
  await expect(page.getByTestId("command-palette")).toHaveCount(0);
  await page.getByRole("button", { name: "Layers", exact: true }).click();
  await expect(page.getByTestId("bottom-sheet-layers")).toBeVisible();
  await expect(page.getByText(/Quick Mode · select and show\/hide layers/)).toBeVisible();
  await expect(page.getByRole("button", { name: /Reorder/ })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Layer actions for/ })).toHaveCount(0);
});

test("phoneBottomSheetAt360", async ({ page }) => {
  await openWorkbench(page, 360, 840);
  await expect(page.getByTestId("phone-shell")).toBeVisible();
  await page.getByRole("button", { name: "Layers", exact: true }).click();
  const layers = page.getByTestId("bottom-sheet-layers");
  await expect(layers).toBeVisible();
  await expect(layers).toHaveAttribute("data-full-height", "true");
  const layersBox = await layers.boundingBox();
  expect(layersBox?.height ?? 0).toBeGreaterThanOrEqual(839);
  await page.getByRole("button", { name: "Close Layers" }).click();
  await page.getByRole("button", { name: "Inspector", exact: true }).click();
  const inspector = page.getByTestId("bottom-sheet-inspector");
  await expect(inspector).toBeVisible();
  await expect(inspector).toHaveAttribute("data-placement", "bottom");
});

test("phoneKeyboardKeepsFieldVisible", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, "visualViewport", {
      configurable: true,
      value: {
        width: 390,
        height: 500,
        offsetTop: 200,
        offsetLeft: 0,
        scale: 1,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      },
    });
  });
  await openWorkbench(page, 390, 844);
  await page.getByRole("button", { name: "Inspector", exact: true }).click();
  const sheet = page.getByTestId("bottom-sheet-inspector");
  await expect(sheet).toHaveAttribute("data-visual-height", "500");
  await expect(sheet).toHaveAttribute("data-visual-offset", "200");
  const textarea = sheet.locator("textarea").first();
  await textarea.focus();
  await expect(textarea).toBeVisible();
  const textareaBox = await textarea.boundingBox();
  expect(textareaBox?.y ?? 0).toBeGreaterThanOrEqual(199);
});

test("tabletDrawersAt768", async ({ page }) => {
  await openWorkbench(page, 768, 1024);
  await expect(page.getByTestId("tablet-shell")).toBeVisible();
  await expect(page.getByTestId("editor-splitter")).toHaveCount(0);
  await page.getByRole("button", { name: "Open layers" }).click();
  await expect(page.getByTestId("bottom-sheet-layers")).toBeVisible();
  await expect(page.getByTestId("bottom-sheet-layers")).toHaveAttribute("data-placement", "left");
});

test("landscapeToolRailAt840", async ({ page }) => {
  await openWorkbench(page, 840, 360);
  await expect(page.getByTestId("editor-shell")).toHaveAttribute("data-mode", "phone");
  const dock = page.getByTestId("phone-tool-dock");
  await expect(dock).toHaveClass(/is-side-rail/);
  const dockBox = await dock.boundingBox();
  const canvasBox = await page.getByTestId("phone-canvas").boundingBox();
  if (!dockBox || !canvasBox) throw new Error("Landscape rail needs layout boxes.");
  expect(dockBox.width).toBeGreaterThanOrEqual(72);
  expect(canvasBox.x).toBeGreaterThanOrEqual(dockBox.x + dockBox.width - 1);
  const scrolls = await page.evaluate(() => document.documentElement.scrollHeight > window.innerHeight + 1);
  expect(scrolls).toBe(false);
});

test("foldUsesTopCanvasBottomTools", async ({ page }) => {
  await page.addInitScript(() => {
    (window as unknown as { viewport: unknown }).viewport = {
      segments: [
        { x: 0, y: 0, width: 360, height: 420 },
        { x: 0, y: 420, width: 360, height: 420 },
      ],
    };
  });
  await openWorkbench(page, 360, 840);
  await expect(page.getByTestId("editor-shell")).toHaveAttribute("data-mode", "fold");
  const fold = page.getByTestId("fold-shell");
  await expect(fold).toHaveAttribute("data-fold", "vertical");
  const canvas = page.getByTestId("fold-canvas");
  const gutter = page.getByTestId("fold-gutter");
  const deck = page.getByTestId("fold-tool-deck");
  const canvasBox = await canvas.boundingBox();
  const gutterBox = await gutter.boundingBox();
  const deckBox = await deck.boundingBox();
  if (!canvasBox || !gutterBox || !deckBox) throw new Error("Fold regions need layout boxes.");
  expect(gutterBox.height).toBeGreaterThanOrEqual(24);
  expect(canvasBox.y + canvasBox.height).toBeLessThanOrEqual(gutterBox.y + 1);
  expect(deckBox.y).toBeGreaterThanOrEqual(gutterBox.y + gutterBox.height - 1);
  await expect(deck.getByRole("tab", { name: "Tools" })).toBeVisible();
  await expect(deck.getByRole("tab", { name: "Layers" })).toBeVisible();
  await expect(deck.getByRole("tab", { name: "Properties" })).toBeVisible();
});

test("foldGutterHasNoControls", async ({ page }) => {
  await page.addInitScript(() => {
    (window as unknown as { viewport: unknown }).viewport = {
      segments: [
        { x: 0, y: 0, width: 360, height: 420 },
        { x: 0, y: 420, width: 360, height: 420 },
      ],
    };
  });
  await openWorkbench(page, 360, 840);
  const gutter = page.getByTestId("fold-gutter");
  await expect(gutter).toBeVisible();
  await expect(gutter.locator("button, input, [role=\"button\"], [tabindex]")).toHaveCount(0);
});

test("foldFallbackWithoutSegments", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "devicePosture", {
      configurable: true,
      value: { type: "folded" },
    });
  });
  await openWorkbench(page, 390, 844);
  await expect(page.getByTestId("phone-shell")).toBeVisible();
  await expect(page.getByTestId("fold-shell")).toHaveCount(0);
});

test("printRootHiddenOnScreen", async ({ page }) => {
  await openWorkbench(page);
  await expect(page.getByTestId("print-root")).toBeHidden();
  await expect(page.getByTestId("print-artboard")).toHaveCount(1);
});

test("printExactArtboard", async ({ page }) => {
  await openWorkbench(page);
  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".ed-shell")).toBeHidden();
  await expect(page.getByTestId("print-root")).toBeVisible();
  const box = await page.getByTestId("print-artboard").boundingBox();
  if (!box) throw new Error("The print artboard has no layout box.");
  expect(Math.round(box.width)).toBe(765);
  expect(Math.round(box.height)).toBe(1000);
});

test("printDoesNotIncludeSelection", async ({ page }) => {
  await openWorkbench(page);
  await expect(page.locator('.ed-canvas [data-layer-id="type-open-studio"]')).toHaveClass(/is-selected/);
  await page.emulateMedia({ media: "print" });
  await expect(page.getByTestId("print-root")).toBeVisible();
  await expect(page.locator(".ed-print-root .is-selected")).toHaveCount(0);
  await expect(page.locator(".ed-print-root [data-handle]")).toHaveCount(0);
  await expect(page.locator(".ed-print-root .selection-overlay")).toHaveCount(0);
  await expect(page.locator(".ed-print-root .ed-layer")).toHaveCount(3);
});

test("emptyDocumentPrintsBackground", async ({ page }) => {
  await openWorkbench(page);
  for (let index = 0; index < 3; index += 1) {
    await page.locator("[data-layer-row]").first().focus();
    await page.keyboard.press("Delete");
  }
  await expect(page.locator("[data-layer-row]")).toHaveCount(0);
  await page.emulateMedia({ media: "print" });
  await expect(page.getByTestId("print-root")).toBeVisible();
  await expect(page.locator(".ed-print-root .ed-layer")).toHaveCount(0);
  const ground = page.locator(".ed-print-root .artboard-ground");
  await expect(ground).toBeVisible();
  const box = await ground.boundingBox();
  expect(Math.round(box?.width ?? 0)).toBe(765);
  expect(Math.round(box?.height ?? 0)).toBe(1000);
});
