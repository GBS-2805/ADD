import { expect, test } from "@playwright/test";

const PNG_1X1 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

async function openWorkbench(page: import("@playwright/test").Page, width = 1440, height = 900) {
  await page.setViewportSize({ width, height });
  await page.goto("/?workbench=1");
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
