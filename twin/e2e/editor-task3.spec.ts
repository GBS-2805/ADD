import { expect, test, type Browser, type Page } from "@playwright/test";

async function openWorkbench(page: Page, width = 1440, height = 900) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expect(page.getByTestId("canvas")).toBeVisible();
}

async function openTouchWorkbench(browser: Browser) {
  const context = await browser.newContext({
    viewport: { width: 360, height: 840 },
    hasTouch: true,
    baseURL: "http://127.0.0.1:5173",
  });
  const page = await context.newPage();
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  return { context, page };
}

async function clickLayer(page: Page, id: string) {
  await page.getByTestId(`art-layer-${id}`).click({ force: true });
  await expect(page.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "1");
}

async function layerStyle(page: Page, id: string) {
  return page.getByTestId(`art-layer-${id}`).getAttribute("style");
}

function styleNumber(style: string | null, property: "left" | "top" | "width" | "height" | "opacity") {
  const match = new RegExp(`${property}:\\s*([-0-9.]+)`).exec(style ?? "");
  return match ? Number(match[1]) : NaN;
}

function styleRotation(style: string | null) {
  const match = /rotate\(([-0-9.]+)deg\)/.exec(style ?? "");
  return match ? Number(match[1]) : 0;
}

function rotatedCorner(left: number, top: number, width: number, height: number, rotation: number, x: -1 | 1, y: -1 | 1) {
  const cx = left + width / 2;
  const cy = top + height / 2;
  const radians = (rotation * Math.PI) / 180;
  const lx = (width / 2) * x;
  const ly = (height / 2) * y;
  return {
    x: cx + lx * Math.cos(radians) - ly * Math.sin(radians),
    y: cy + lx * Math.sin(radians) + ly * Math.cos(radians),
  };
}

async function zoom200At(page: Page, id: string) {
  const layer = await page.getByTestId(`art-layer-${id}`).boundingBox();
  if (!layer) throw new Error("Layer has no layout box.");
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
}

test("pinchZoomsAt360", async ({ browser }) => {
  const { context, page } = await openTouchWorkbench(browser);
  const beforeZoom = Number(await page.getByTestId("canvas").getAttribute("data-zoom"));
  const beforeLayer = await layerStyle(page, "shape-orbit");
  const canvas = await page.getByTestId("canvas").boundingBox();
  if (!canvas) throw new Error("Canvas has no layout box.");
  const client = await context.newCDPSession(page);
  const cx = canvas.x + canvas.width / 2;
  const cy = canvas.y + canvas.height / 2;
  await client.send("Input.dispatchTouchEvent", {
    type: "touchStart",
    touchPoints: [{ x: cx - 40, y: cy, id: 1 }, { x: cx + 40, y: cy, id: 2 }],
  });
  await client.send("Input.dispatchTouchEvent", {
    type: "touchMove",
    touchPoints: [{ x: cx - 100, y: cy, id: 1 }, { x: cx + 100, y: cy, id: 2 }],
  });
  await client.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect.poll(async () => Number(await page.getByTestId("canvas").getAttribute("data-zoom"))).toBeGreaterThan(beforeZoom);
  expect(await layerStyle(page, "shape-orbit")).toBe(beforeLayer);
  await context.close();
});

test("wheelZoomDoesNotScrollPage", async ({ page }) => {
  await openWorkbench(page);
  const canvas = page.getByTestId("canvas");
  const beforeZoom = Number(await canvas.getAttribute("data-zoom"));
  const prevented = await page.evaluate(() => {
    const target = document.querySelector<HTMLElement>('[data-testid="canvas"]');
    if (!target) return false;
    const box = target.getBoundingClientRect();
    const event = new WheelEvent("wheel", {
      deltaY: -120,
      ctrlKey: true,
      clientX: box.x + box.width / 2,
      clientY: box.y + box.height / 2,
      bubbles: true,
      cancelable: true,
    });
    target.dispatchEvent(event);
    return event.defaultPrevented;
  });
  expect(prevented).toBe(true);
  await expect.poll(async () => Number(await canvas.getAttribute("data-zoom"))).toBeGreaterThan(beforeZoom);
  expect(await page.evaluate(() => window.scrollY)).toBe(0);
});

test("twoFingerPinchDoesNotMoveLayer", async ({ browser }) => {
  const { context, page } = await openTouchWorkbench(browser);
  const beforeLayer = await layerStyle(page, "shape-star");
  const canvas = await page.getByTestId("canvas").boundingBox();
  if (!canvas) throw new Error("Canvas has no layout box.");
  const client = await context.newCDPSession(page);
  const cx = canvas.x + canvas.width / 2;
  const cy = canvas.y + canvas.height / 2;
  await client.send("Input.dispatchTouchEvent", {
    type: "touchStart",
    touchPoints: [{ x: cx, y: cy - 40, id: 3 }, { x: cx, y: cy + 40, id: 4 }],
  });
  await client.send("Input.dispatchTouchEvent", {
    type: "touchMove",
    touchPoints: [{ x: cx, y: cy - 100, id: 3 }, { x: cx, y: cy + 100, id: 4 }],
  });
  await client.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  expect(await layerStyle(page, "shape-star")).toBe(beforeLayer);
  await context.close();
});

test("dragsASelectionAndUndoesOnce", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  const box = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!box) throw new Error("Orbit has no layout box.");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2 + 20, { steps: 3 });
  await page.mouse.up();
  expect(await layerStyle(page, "shape-orbit")).not.toBe(before);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect.poll(async () => layerStyle(page, "shape-orbit")).toBe(before);
});

test("shiftClickBuildsMultiSelection", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  await page.getByTestId("art-layer-shape-star").click({ force: true, modifiers: ["Shift"] });
  await expect(page.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "2");
  await expect(page.getByTestId("selection-overlay").locator(".ed-selection-multi")).toBeVisible();
});

test("marqueeSelectsTwoLayers", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  const artboard = await page.getByTestId("editor-artboard").boundingBox();
  if (!artboard) throw new Error("Artboard has no layout box.");
  await page.mouse.move(artboard.x + 4, artboard.y + artboard.height - 4);
  await page.mouse.down();
  await page.mouse.move(artboard.x + artboard.width * 0.8, artboard.y + 2, { steps: 5 });
  await page.mouse.up();
  await expect.poll(async () => Number(await page.getByTestId("selection-overlay").getAttribute("data-selection-count"))).toBeGreaterThanOrEqual(2);
});

test("snapGuideAppearsAtCenter", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const artboard = await page.getByTestId("editor-artboard").boundingBox();
  const orbit = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!artboard || !orbit) throw new Error("Missing layout boxes.");
  const startX = orbit.x + orbit.width / 2;
  const startY = orbit.y + orbit.height / 2;
  const targetX = startX + (artboard.x + artboard.width / 2 - (orbit.x + orbit.width / 2));
  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(targetX, startY, { steps: 4 });
  await expect(page.locator('[data-guide-axis="x"]')).toBeVisible();
  await page.mouse.up();
});

test("cmdDisablesSnapping", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const artboard = await page.getByTestId("editor-artboard").boundingBox();
  const orbit = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!artboard || !orbit) throw new Error("Missing layout boxes.");
  const startX = orbit.x + orbit.width / 2;
  const startY = orbit.y + orbit.height / 2;
  const targetX = startX + (artboard.x + artboard.width / 2 - (orbit.x + orbit.width / 2));
  await page.keyboard.down("Meta");
  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(targetX, startY, { steps: 4 });
  await expect(page.locator('[data-guide-axis="x"]')).toHaveCount(0);
  await page.mouse.up();
  await page.keyboard.up("Meta");
});

test("touchMoveWorksAt360", async ({ browser }) => {
  const { context, page } = await openTouchWorkbench(browser);
  const before = await layerStyle(page, "shape-orbit");
  const box = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!box) throw new Error("Orbit has no layout box.");
  const client = await context.newCDPSession(page);
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await client.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x, y, id: 7 }] });
  await client.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: x + 30, y: y + 10, id: 7 }] });
  await client.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect.poll(async () => layerStyle(page, "shape-orbit")).not.toBe(before);
  await context.close();
});

test("resizesEastHandle", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  const handle = await page.locator('[data-handle="e"]').boundingBox();
  if (!handle) throw new Error("East handle has no layout box.");
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(handle.x + handle.width / 2 + 50, handle.y + handle.height / 2, { steps: 3 });
  await page.mouse.up();
  expect(styleNumber(await layerStyle(page, "shape-orbit"), "width")).toBeGreaterThan(styleNumber(before, "width"));
});

test("shiftCornerPreservesRatio", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  const ratio = styleNumber(before, "width") / styleNumber(before, "height");
  const handle = await page.locator('[data-handle="se"]').boundingBox();
  if (!handle) throw new Error("Corner handle has no layout box.");
  await page.keyboard.down("Shift");
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(handle.x + handle.width / 2 + 45, handle.y + handle.height / 2 + 20, { steps: 3 });
  await page.mouse.up();
  await page.keyboard.up("Shift");
  const after = await layerStyle(page, "shape-orbit");
  expect(styleNumber(after, "width") / styleNumber(after, "height")).toBeCloseTo(ratio, 1);
});

test("rotatesAndShiftSnaps", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const handle = await page.locator('[data-handle="rotate"]').boundingBox();
  if (!handle) throw new Error("Rotation handle has no layout box.");
  await page.keyboard.down("Shift");
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(handle.x + 160, handle.y + handle.height / 2 + 60, { steps: 4 });
  await page.mouse.up();
  await page.keyboard.up("Shift");
  const rotation = styleRotation(await layerStyle(page, "shape-orbit"));
  expect(Math.abs(rotation % 15)).toBeLessThan(0.001);
});

test("resizeAt200PercentTracksPointer", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-100").click();
  await zoom200At(page, "shape-orbit");
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  const handle = await page.locator('[data-handle="e"]').boundingBox();
  if (!handle) throw new Error("East handle has no layout box.");
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(handle.x + handle.width / 2 + 60, handle.y + handle.height / 2, { steps: 3 });
  await page.mouse.up();
  const delta = styleNumber(await layerStyle(page, "shape-orbit"), "width") - styleNumber(before, "width");
  expect(delta).toBeGreaterThan(25);
  expect(delta).toBeLessThan(35);
});

test("resizeKeepsOppositeCornerFixedAt37Degrees", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-100").click();
  await zoom200At(page, "shape-orbit");
  await clickLayer(page, "shape-orbit");
  await page.getByTestId("numeric-rotation").fill("37");
  await page.getByTestId("numeric-rotation").press("Enter");
  await expect.poll(async () => styleRotation(await layerStyle(page, "shape-orbit"))).toBeCloseTo(37, 0);
  const before = await layerStyle(page, "shape-orbit");
  const beforeCorner = rotatedCorner(
    styleNumber(before, "left"), styleNumber(before, "top"),
    styleNumber(before, "width"), styleNumber(before, "height"), 37, 1, 1,
  );
  const handle = await page.locator('[data-handle="nw"]').boundingBox();
  if (!handle) throw new Error("Corner handle has no layout box.");
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(handle.x + handle.width / 2 - 60, handle.y + handle.height / 2 - 60, { steps: 4 });
  await page.mouse.up();
  const after = await layerStyle(page, "shape-orbit");
  expect(styleNumber(after, "width")).toBeGreaterThan(styleNumber(before, "width"));
  const afterCorner = rotatedCorner(
    styleNumber(after, "left"), styleNumber(after, "top"),
    styleNumber(after, "width"), styleNumber(after, "height"), 37, 1, 1,
  );
  expect(Math.hypot(afterCorner.x - beforeCorner.x, afterCorner.y - beforeCorner.y)).toBeLessThanOrEqual(0.5);
});

test("pointerCancelLeavesDocumentUnchanged", async ({ page }) => {
  await openWorkbench(page);
  await page.getByTestId("zoom-fit").click();
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  const box = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!box) throw new Error("Orbit has no layout box.");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 50, box.y + box.height / 2, { steps: 3 });
  await page.locator('[data-testid="canvas"]').dispatchEvent("pointercancel", { pointerId: 1, pointerType: "mouse" });
  await page.mouse.up();
  expect(await layerStyle(page, "shape-orbit")).toBe(before);
});

test("handlesDoNotEnterTabOrder", async ({ page }) => {
  await openWorkbench(page);
  await clickLayer(page, "shape-orbit");
  const tabs = await page.locator("[data-handle]").evaluateAll((handles) => handles.map((handle) => handle.getAttribute("tabindex")));
  expect(tabs.length).toBeGreaterThan(0);
  expect(tabs.every((value) => value === "-1")).toBe(true);
});

test("touchResizeHandleAt360", async ({ browser }) => {
  const { context, page } = await openTouchWorkbench(browser);
  const layer = await page.getByTestId("art-layer-shape-orbit").boundingBox();
  if (!layer) throw new Error("Orbit has no layout box.");
  await page.touchscreen.tap(layer.x + layer.width / 2, layer.y + layer.height / 2);
  await expect(page.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "1");
  const before = await layerStyle(page, "shape-orbit");
  const handle = await page.locator('[data-handle="e"]').boundingBox();
  if (!handle) throw new Error("East handle has no layout box.");
  const client = await context.newCDPSession(page);
  const x = handle.x + handle.width / 2;
  const y = handle.y + handle.height / 2;
  await client.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x, y, id: 9 }] });
  await client.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: x + 40, y, id: 9 }] });
  await client.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect.poll(async () => styleNumber(await layerStyle(page, "shape-orbit"), "width")).toBeGreaterThan(styleNumber(before, "width"));
  await context.close();
});

test("inspectorEditsTransform", async ({ page }) => {
  await openWorkbench(page);
  await clickLayer(page, "shape-orbit");
  const before = await layerStyle(page, "shape-orbit");
  await page.getByTestId("numeric-x").fill("120");
  await page.getByTestId("numeric-x").press("Enter");
  await expect.poll(async () => styleNumber(await layerStyle(page, "shape-orbit"), "left")).not.toBe(styleNumber(before, "left"));
  await page.getByRole("button", { name: "Undo" }).click();
  await expect.poll(async () => styleNumber(await layerStyle(page, "shape-orbit"), "left")).toBeCloseTo(styleNumber(before, "left"));
});

test("inspectorEditsOpacity", async ({ page }) => {
  await openWorkbench(page);
  await clickLayer(page, "shape-orbit");
  await page.getByTestId("numeric-opacity").fill("50");
  await page.getByTestId("numeric-opacity").press("Enter");
  await expect.poll(async () => styleNumber(await layerStyle(page, "shape-orbit"), "opacity")).toBeCloseTo(0.5);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect.poll(async () => styleNumber(await layerStyle(page, "shape-orbit"), "opacity")).toBeCloseTo(1);
});
