import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

const importedProject = {
  format: "bmo-twin-project",
  version: 1,
  savedAt: "2026-11-15T00:00:00.000Z",
  document: {
    background: { kind: "solid", hex: "#fafaf7" },
    selectedLayerId: "imported-headline",
    layers: [
      {
        id: "imported-headline",
        name: "Type / IMPORTED",
        kind: "text",
        effect: null,
        blend: "normal",
        opacity: 1,
        transform: { x: 0, y: 0, scale: 1, rotation: 0 },
        visible: true,
        text: { content: "IMPORTED\nPROJECT", size: 56, align: "center", font: "instrument-serif" },
        fill: "#111111",
      },
    ],
  },
};

test("exports a portable .twin project file", async ({ page }) => {
  await page.goto("/");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Save project file/ }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("open-studio.twin");
  const path = await download.path();
  if (!path) throw new Error("The project download has no path.");
  const project = JSON.parse(await readFile(path, "utf8")) as {
    format: string;
    version: number;
    document: { layers: Array<{ id: string; kind: string }> };
  };
  expect(project.format).toBe("bmo-twin-project");
  expect(project.version).toBe(2);
  expect(project.document.layers.map((layer) => layer.id)).toEqual([
    "type-open-studio",
    "shape-star",
    "shape-orbit",
  ]);
});

test("embeds imported PNG layers in the project file", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^PNG/ }).click();
  await page.getByTestId("overlay-import-input").setInputFiles("e2e/fixtures/layer.png");

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Save project file/ }).click();
  const download = await downloadPromise;
  const path = await download.path();
  if (!path) throw new Error("The project download has no path.");
  const project = JSON.parse(await readFile(path, "utf8")) as {
    document: { layers: Array<{ kind: string; image?: { src: string } }> };
  };
  const image = project.document.layers.find((layer) => layer.kind === "image");
  expect(image?.image?.src).toMatch(/^data:image\/png;base64,/);
});

test("exports a flattened PNG of the full artboard", async ({ page }) => {
  await page.goto("/");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open actions" }).click();
  await expect(page.getByRole("button", { name: /Export PNG/ })).toBeVisible();
  await page.getByRole("button", { name: /Export PNG/ }).click();

  const status = page.getByTestId("png-export-result");
  await expect(status).toHaveAttribute("data-filename", "open-studio.png");
  await expect(status).toHaveAttribute("data-width", "1530");
  await expect(status).toHaveAttribute("data-height", "2000");
  await expect(status).toHaveAttribute("data-signature", "89504e470d0a1a0a");

  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("open-studio.png");
  const path = await download.path();
  if (!path) throw new Error("The PNG download has no path.");
  const bytes = await readFile(path);
  expect(bytes.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");
  expect(bytes.subarray(12, 16).toString("ascii")).toBe("IHDR");
  expect(bytes.readUInt32BE(16)).toBe(1530);
  expect(bytes.readUInt32BE(20)).toBe(2000);
  expect(bytes.byteLength).toBeGreaterThan(10_000);

  const decoded = await page.evaluate(async (source) => {
    const response = await fetch(`data:image/png;base64,${source}`);
    const bitmap = await createImageBitmap(await response.blob());
    const canvas = document.createElement("canvas");
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("No decode context");
    context.drawImage(bitmap, 0, 0);
    const pixels = context.getImageData(0, 0, bitmap.width, bitmap.height).data;
    let opaque = 0;
    for (let index = 3; index < pixels.length; index += 4003) {
      if (pixels[index] > 0) opaque += 1;
    }
    return { width: bitmap.width, height: bitmap.height, opaque };
  }, bytes.toString("base64"));
  expect(decoded.width).toBe(1530);
  expect(decoded.height).toBe(2000);
  expect(decoded.opaque).toBeGreaterThan(100);

  await expect(page.locator(".art-layer")).toHaveCount(3);
  await page.keyboard.press("Control+z");
  await expect(page.locator(".art-layer")).toHaveCount(3);
  await expect(page.getByRole("status")).toContainText("PNG exported");
});

test("print isolates the artboard and invokes the browser print flow", async ({ page }) => {
  await page.addInitScript(() => {
    window.print = () => {
      (window as { __printCalled?: boolean }).__printCalled = true;
    };
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Print \/ Save PDF/ }).click();
  await expect(page.locator("body")).toHaveClass(/is-printing/);
  expect(await page.evaluate(() => (window as { __printCalled?: boolean }).__printCalled)).toBe(true);

  await page.emulateMedia({ media: "print" });
  await expect(page.getByTestId("artboard")).toHaveCSS("position", "fixed");
  expect(await page.locator(".twin-header").evaluate((element) => getComputedStyle(element).visibility)).toBe("hidden");
  expect(await page.getByTestId("artboard").evaluate((element) => getComputedStyle(element).visibility)).toBe("visible");
});

test("opens a .twin project, replaces the document and resets edit history", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  await expect(page.locator(".art-layer-text")).toHaveCount(2);
  const chooserPromise = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Open project file/ }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles({
    name: "imported.twin",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(importedProject)),
  });

  await expect(page.getByText("IMPORTED", { exact: true })).toBeVisible();
  await expect(page.getByText("PROJECT", { exact: true })).toBeVisible();
  await expect(page.locator(".artboard-ground .effect-solid")).toBeVisible();
  await expect(page.locator(".art-layer")).toHaveCount(1);
  await expect(page.getByRole("status")).toContainText("Project opened.");

  await page.keyboard.press("Control+z");
  await expect(page.locator(".art-layer")).toHaveCount(1);
  await expect(page.getByText("IMPORTED", { exact: true })).toBeVisible();
});
