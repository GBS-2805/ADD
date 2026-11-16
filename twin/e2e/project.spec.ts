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

const emptyProject = {
  format: "bmo-twin-project",
  version: 2,
  savedAt: "2026-11-16T00:00:00.000Z",
  document: {
    background: { kind: "solid", hex: "#fafaf7" },
    layers: [],
    selectedLayerIds: [],
  },
};

async function openEditor(page: import("@playwright/test").Page, width = 1440, height = 900) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

async function openDocumentMenu(page: import("@playwright/test").Page) {
  await page.getByRole("button", { name: "Open document menu" }).click();
}

test("exports a portable .twin project file", async ({ page }) => {
  await openEditor(page);
  const downloadPromise = page.waitForEvent("download");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Save .twin" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("open-studio.twin");
  const path = await download.path();
  if (!path) throw new Error("The project download has no path.");
  const project = JSON.parse(await readFile(path, "utf8")) as {
    format: string;
    version: number;
    document: {
      selectedLayerIds: string[];
      layers: Array<{
        id: string;
        kind: string;
        transform: { x: number; y: number; scaleX: number; scaleY: number; rotation: number };
      }>;
    };
  };
  expect(project.format).toBe("bmo-twin-project");
  expect(project.version).toBe(2);
  expect(project.document.layers.map((layer) => layer.id)).toEqual([
    "type-open-studio",
    "shape-star",
    "shape-orbit",
  ]);
  expect(project.document.selectedLayerIds).toEqual(["type-open-studio"]);
  const headline = project.document.layers[0].transform;
  expect(headline.scaleX).toBe(1);
  expect(headline.scaleY).toBe(1);
  expect("scale" in headline).toBe(false);
});

test("embeds imported PNG layers in the project file", async ({ page }) => {
  await openEditor(page);
  await page.getByTestId("editor-image-input").setInputFiles("e2e/fixtures/layer.png");
  await expect(page.locator(".ed-canvas .ed-layer-image")).toHaveCount(1);

  const downloadPromise = page.waitForEvent("download");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Save .twin" }).click();
  const download = await downloadPromise;
  const path = await download.path();
  if (!path) throw new Error("The project download has no path.");
  const project = JSON.parse(await readFile(path, "utf8")) as {
    document: { layers: Array<{ kind: string; image?: { src: string } }> };
  };
  const image = project.document.layers.find((layer) => layer.kind === "image");
  expect(image?.image?.src).toMatch(/^data:image\/png;base64,/);
});

test("exports a flattened PNG of the full 765 x 1000 artboard", async ({ page }) => {
  await openEditor(page);
  const downloadPromise = page.waitForEvent("download");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Export PNG" }).click();

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

  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(3);
  await page.keyboard.press("Control+z");
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(3);
  await expect(page.locator(".ed-toast")).toContainText("PNG exported");
});

test("print uses the dedicated artboard and never the editor chrome", async ({ page }) => {
  await page.addInitScript(() => {
    window.print = () => {
      (window as { __printCalled?: boolean }).__printCalled = true;
    };
  });
  await openEditor(page);
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Print / Save PDF" }).click();
  await expect(page.locator("body")).not.toHaveClass(/is-printing/);
  expect(await page.evaluate(() => (window as { __printCalled?: boolean }).__printCalled)).toBe(true);

  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".ed-shell")).toBeHidden();
  await expect(page.getByTestId("print-root")).toBeVisible();
  const box = await page.getByTestId("print-artboard").boundingBox();
  if (!box) throw new Error("The print artboard has no layout box.");
  expect(Math.round(box.width)).toBe(765);
  expect(Math.round(box.height)).toBe(1000);
});

test("opens a .twin project, replaces the document and resets edit history", async ({ page }) => {
  await openEditor(page);
  await page.getByRole("button", { name: "Add layer" }).click();
  await page.getByRole("menuitem", { name: "Text" }).click();
  await expect(page.locator(".ed-canvas .ed-layer-text")).toHaveCount(2);
  const chooserPromise = page.waitForEvent("filechooser");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Open .twin" }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles({
    name: "imported.twin",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(importedProject)),
  });

  await expect(page.locator(".ed-canvas").getByText("IMPORTED", { exact: true })).toBeVisible();
  await expect(page.locator(".ed-canvas").getByText("PROJECT", { exact: true })).toBeVisible();
  await expect(page.locator(".ed-canvas .artboard-ground .effect-solid")).toBeVisible();
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(1);
  await expect(page.locator(".ed-toast")).toContainText("Project opened.");

  await page.keyboard.press("Control+z");
  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(1);
  await expect(page.locator(".ed-canvas").getByText("IMPORTED", { exact: true })).toBeVisible();
});

test("round-trips an empty document and keeps it printable", async ({ page }) => {
  await openEditor(page);
  const chooserPromise = page.waitForEvent("filechooser");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Open .twin" }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles({
    name: "empty.twin",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(emptyProject)),
  });

  await expect(page.locator(".ed-canvas .art-layer")).toHaveCount(0);
  await expect(page.getByText("No layers. Add text, a shape or a PNG.")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await openDocumentMenu(page);
  await page.getByRole("menuitem", { name: "Save .twin" }).click();
  const download = await downloadPromise;
  const path = await download.path();
  if (!path) throw new Error("The empty project download has no path.");
  const saved = JSON.parse(await readFile(path, "utf8")) as { document: { layers: unknown[]; selectedLayerIds: string[] } };
  expect(saved.document.layers).toEqual([]);
  expect(saved.document.selectedLayerIds).toEqual([]);

  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".ed-print-root .artboard-ground")).toBeVisible();
  const box = await page.getByTestId("print-artboard").boundingBox();
  expect(Math.round(box?.width ?? 0)).toBe(765);
  expect(Math.round(box?.height ?? 0)).toBe(1000);
});
