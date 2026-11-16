import { expect, test } from "@playwright/test";

async function openEditor(page: import("@playwright/test").Page, width = 1440, height = 900) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

test("shellUsesApprovedPalette", async ({ page }) => {
  await openEditor(page);
  const palette = await page.getByTestId("editor-shell").evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      studio: style.getPropertyValue("--ed-studio").trim(),
      cream: style.getPropertyValue("--ed-cream").trim(),
      red: style.getPropertyValue("--ed-red").trim(),
      muted: style.getPropertyValue("--ed-muted").trim(),
    };
  });
  expect(palette).toEqual({
    studio: "#131513",
    cream: "#f2eee3",
    red: "#dd4124",
    muted: "#a9aa9f",
  });
  await expect(page.getByTestId("editor-shell")).toHaveCSS("background-color", "rgb(19, 21, 19)");
});

test("toolbarTargetsAreAtLeast44px", async ({ page }) => {
  await openEditor(page);
  const sizes = await page.locator(".ed-tool-button").evaluateAll((buttons) =>
    buttons.map((button) => {
      const rect = button.getBoundingClientRect();
      return { width: rect.width, height: rect.height };
    }),
  );
  expect(sizes.length).toBeGreaterThanOrEqual(5);
  for (const size of sizes) {
    expect(size.width).toBeGreaterThanOrEqual(44);
    expect(size.height).toBeGreaterThanOrEqual(44);
  }
});

test("addMenuCreatesTextAndShape", async ({ page }) => {
  await openEditor(page);
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(3);
  await page.getByRole("button", { name: "Add layer" }).click();
  await page.getByRole("menuitem", { name: "Text" }).click();
  await expect(page.locator(".ed-canvas .art-layer-text")).toHaveCount(2);
  await page.getByRole("button", { name: "Add layer" }).click();
  await page.getByRole("menuitem", { name: "Shape / Star" }).click();
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(5);
});

test("addsPngLayerAndChangesBlend", async ({ page }) => {
  await openEditor(page);
  await page.getByTestId("editor-image-input").setInputFiles("e2e/fixtures/layer.png");
  const image = page.locator(".ed-canvas .ed-layer-image");
  await expect(page.locator(".ed-canvas .ed-layer-image")).toHaveCount(1);
  await image.click({ force: true });
  const blend = page.getByRole("combobox", { name: "Blend mode" });
  await blend.click();
  await page.getByRole("option", { name: "Multiply" }).click();
  await expect(page.locator('.ed-canvas [data-testid^="art-layer-png-"]')).toHaveCSS("mix-blend-mode", "multiply");
});

test("fontChoiceUpdatesText", async ({ page }) => {
  await openEditor(page);
  await page.locator(".ed-canvas .art-layer-text").first().click({ force: true });
  const font = page.getByRole("combobox", { name: "Font" });
  await font.click();
  await page.getByRole("option", { name: /Libre Franklin/ }).click();
  await expect(page.locator(".ed-canvas .ed-layer-text").first()).toHaveCSS("font-family", /Libre Franklin/);
});

test("opensEveryPluginWithoutPageErrors", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await openEditor(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await expect(page.getByTestId("effects-panel")).toBeVisible();
  for (const id of [
    "shader-lab",
    "texture-lab",
    "particle-field",
    "pattern-library",
    "spatial-dock",
    "native-parity",
    "asset-shelf",
    "overlay-lab",
  ]) {
    await page.getByTestId(`catalogue-${id}`).click();
    await expect(page.getByTestId(`plugin-${id}`)).toBeVisible();
    await page.getByRole("button", { name: "Close", exact: true }).click();
    await expect(page.getByTestId(`plugin-${id}`)).toHaveCount(0);
  }
  expect(errors).toEqual([]);
});

test("nativeLensStillDimsOnly", async ({ page }) => {
  await openEditor(page);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  const cards = page.locator(".plugin-card");
  await expect(cards).toHaveCount(8);
  await page.getByRole("button", { name: "Native lens" }).click();
  await expect(page.locator(".plugin-card.is-lens-dimmed").first()).toBeVisible();
  await expect(page.locator(".plugin-card").first()).toBeEnabled();
  await page.getByRole("button", { name: "Native lens" }).click();
  await expect(page.locator(".plugin-card.is-lens-dimmed")).toHaveCount(0);
});

test("desktopUsesSplitterAndKeyboardResize", async ({ page }) => {
  await openEditor(page);
  const splitter = page.getByTestId("editor-splitter");
  await expect(splitter).toBeVisible();
  const before = await splitter.getAttribute("data-sizes");
  const seam = page.getByRole("separator", { name: "Resize layers and canvas" });
  await seam.focus();
  await page.keyboard.press("ArrowRight");
  await expect(splitter).not.toHaveAttribute("data-sizes", before ?? "");
  await page.getByRole("button", { name: "Collapse layers" }).click();
  await expect(page.getByTestId("splitter-panel-layers")).toHaveAttribute("data-collapsed", "true");
});

test("reducedMotionKeepsFeedbackStatic", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await openEditor(page);
  const duration = await page.locator(".ed-tool-button").first().evaluate(
    (element) => getComputedStyle(element).transitionDuration,
  );
  expect(duration).toMatch(/0\.01ms|1e-05s|0s/);
});

test("appMakesNoExternalRequests", async ({ page }) => {
  const external: string[] = [];
  page.on("request", (request) => {
    const url = request.url();
    if (!url.startsWith("http://127.0.0.1:5173") && !url.startsWith("data:") && !url.startsWith("blob:")) {
      external.push(url);
    }
  });
  await openEditor(page);
  await page.waitForTimeout(500);
  expect(external).toEqual([]);
});
