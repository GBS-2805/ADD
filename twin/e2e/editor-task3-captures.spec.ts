import { expect, test, type Page } from "@playwright/test";

const CAPTURES = "../artifacts/twin/editor";

async function openWorkbench(page: Page) {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expect(page.getByTestId("canvas")).toBeVisible();
}

async function fit(page: Page) {
  await page.getByTestId("zoom-fit").click();
  await expect.poll(async () => Number(await page.getByTestId("canvas").getAttribute("data-zoom"))).toBeGreaterThan(0.5);
}

async function clickLayer(page: Page, id: string) {
  await page.getByTestId(`art-layer-${id}`).click({ force: true });
  await expect(page.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "1");
  await page.mouse.move(1200, 120);
  await expect(page.locator("[data-guide-axis]")).toHaveCount(0);
}

async function capture(page: Page, name: string) {
  await page.screenshot({ path: `${CAPTURES}/${name}.png`, animations: "disabled" });
}

test("task3Captures", async ({ page }) => {
  await openWorkbench(page);
  await fit(page);
  await capture(page, "canvas-fit");

  await clickLayer(page, "shape-orbit");
  await capture(page, "resize-handles");
  await capture(page, "inspector-single");

  await page.getByTestId("numeric-rotation").fill("15");
  await page.getByTestId("numeric-rotation").press("Enter");
  await expect.poll(async () => page.getByTestId("art-layer-shape-orbit").getAttribute("style")).toContain("rotate(15deg)");
  await capture(page, "rotate-15");

  await page.getByTestId("numeric-rotation").fill("0");
  await page.getByTestId("numeric-rotation").press("Enter");
  await expect.poll(async () => page.getByTestId("art-layer-shape-orbit").getAttribute("style")).toContain("rotate(0deg)");
  await page.locator('[data-ref="nw"]').click();
  await capture(page, "reference-point");

  await page.reload();
  await expect(page.getByTestId("canvas")).toBeVisible();
  await fit(page);
  await clickLayer(page, "shape-orbit");
  await page.getByTestId("art-layer-shape-star").click({ force: true, modifiers: ["Shift"] });
  await expect(page.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "2");
  await capture(page, "selection-multi");
  await capture(page, "inspector-multi");

  await page.reload();
  await expect(page.getByTestId("canvas")).toBeVisible();
  await fit(page);
  await clickLayer(page, "shape-orbit");
  const artboard = await page.getByTestId("editor-artboard").boundingBox();
  const orbit = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!artboard || !orbit) throw new Error("Missing layout boxes for snap capture.");
  const startX = orbit.x + orbit.width / 2;
  const startY = orbit.y + orbit.height / 2;
  const targetX = startX + (artboard.x + artboard.width / 2 - (orbit.x + orbit.width / 2));
  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(targetX, startY, { steps: 4 });
  await expect(page.locator('[data-guide-axis="x"]')).toBeVisible();
  await capture(page, "snap-center");
  await page.mouse.up();

  await page.reload();
  await expect(page.getByTestId("canvas")).toBeVisible();
  await page.getByTestId("zoom-100").click();
  const layer = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!layer) throw new Error("Orbit has no layout box for zoom capture.");
  await page.evaluate(({ x, y }) => {
    const target = document.querySelector<HTMLElement>('[data-testid="canvas"]');
    if (!target) return;
    target.dispatchEvent(new WheelEvent("wheel", {
      deltaY: -Math.log(2) / 0.002,
      ctrlKey: true,
      clientX: x,
      clientY: y,
      bubbles: true,
      cancelable: true,
    }));
  }, { x: layer.x + layer.width / 2, y: layer.y + layer.height / 2 });
  await expect.poll(async () => Number(await page.getByTestId("canvas").getAttribute("data-zoom"))).toBeCloseTo(2, 2);
  await capture(page, "canvas-200");
});
