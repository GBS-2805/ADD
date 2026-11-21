import { chromium } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const BASE_URL = process.env.AUDIT_BASE_URL ?? "https://site-b5c3eb4162ed46148d22881fe0e65300.freebuff.page/";
const OUT = path.resolve(process.env.AUDIT_OUT ?? "../artifacts/twin/spec-conformance");
const VIEWPORT = { width: 390, height: 844 };
const PNG_SIGNATURE = "89504e470d0a1a0a";

await fs.mkdir(OUT, { recursive: true });

const pageErrors = [];
const consoleErrors = [];
const externalRequests = [];
const checks = [];
let page;
let browser;
let scriptSource = null;
let styleSource = null;

const check = (id, pass, detail) => {
  checks.push({ id, pass: Boolean(pass), detail });
  console.log(`${pass ? "PASS" : "FAIL"} ${id}${detail === undefined ? "" : ` — ${JSON.stringify(detail)}`}`);
};

const fail = (message) => {
  throw new Error(message);
};

try {
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
  });
  const context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1 });
  page = await context.newPage();
  page.on("pageerror", (error) => pageErrors.push(String(error)));
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("request", (request) => {
    const url = request.url();
    if (!url.startsWith(BASE_URL) && !url.startsWith("data:") && !url.startsWith("blob:") && !url.startsWith("https://static.cloudflareinsights.com/")) {
      externalRequests.push(url);
    }
  });

  await page.goto(BASE_URL, { waitUntil: "networkidle" });
  await page.getByTestId("stage").waitFor({ state: "visible" });
  await page.waitForTimeout(350);

  scriptSource = await page.evaluate(() =>
    [...document.scripts]
      .map((script) => script.getAttribute("src"))
      .filter((src) => src && /\/assets\/index-[^/]+\.js$/.test(src))
      .at(-1) ?? null,
  );
  styleSource = await page.evaluate(() =>
    [...document.querySelectorAll('link[rel="stylesheet"]')]
      .map((link) => link.getAttribute("href"))
      .filter((href) => href && /\/assets\/index-[^/]+\.css$/.test(href))
      .at(-1) ?? null,
  );

  // Save/open/export are all File commands in the Actions sheet.
  const projectDownloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Save project file/ }).click();
  const projectDownload = await projectDownloadPromise;
  const savedPath = await projectDownload.path();
  if (!savedPath) fail("The saved project download has no path.");
  const saved = JSON.parse(await fs.readFile(savedPath, "utf8"));
  check("save.twin", projectDownload.suggestedFilename() === "open-studio.twin" && saved.format === "bmo-twin-project" && saved.version === 1 && saved.document.layers.length === 3, {
    filename: projectDownload.suggestedFilename(),
    format: saved.format,
    version: saved.version,
    layers: saved.document.layers.length,
  });

  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  const beforeUndo = await page.locator(".art-layer").count();
  await page.keyboard.press("Control+z");
  await page.waitForTimeout(180);
  const afterUndo = await page.locator(".art-layer").count();
  check("history.add-undo", beforeUndo === 4 && afterUndo === 3, { beforeUndo, afterUndo });

  // PNG export: the downloaded file must be a real PNG with the canonical artboard dimensions.
  const pngDownloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Export PNG/ }).click();
  const pngDownload = await pngDownloadPromise;
  const pngPath = await pngDownload.path();
  if (!pngPath) fail("The PNG download has no path.");
  const pngBytes = await fs.readFile(pngPath);
  const png = {
    filename: pngDownload.suggestedFilename(),
    signature: pngBytes.subarray(0, 8).toString("hex"),
    chunk: pngBytes.subarray(12, 16).toString("ascii"),
    width: pngBytes.readUInt32BE(16),
    height: pngBytes.readUInt32BE(20),
    bytes: pngBytes.byteLength,
  };
  const exportStatus = page.getByTestId("png-export-result");
  const exportStatusData = {
    width: await exportStatus.getAttribute("data-width"),
    height: await exportStatus.getAttribute("data-height"),
    filename: await exportStatus.getAttribute("data-filename"),
    signature: await exportStatus.getAttribute("data-signature"),
  };
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
  }, pngBytes.toString("base64"));
  await fs.writeFile(path.join(OUT, "live-png-export.png"), pngBytes);

  check(
    "png.export",
    png.filename === "open-studio.png" &&
      png.signature === PNG_SIGNATURE &&
      png.chunk === "IHDR" &&
      png.width === 1530 &&
      png.height === 2000 &&
      png.bytes > 10_000 &&
      exportStatusData.width === "1530" &&
      exportStatusData.height === "2000" &&
      exportStatusData.filename === "open-studio.png" &&
      exportStatusData.signature === PNG_SIGNATURE &&
      decoded.width === 1530 &&
      decoded.height === 2000 &&
      decoded.opaque > 100,
    { ...png, status: exportStatusData, decoded },
  );

  const layersAfterExport = await page.locator(".art-layer").count();
  await page.keyboard.press("Control+z");
  await page.waitForTimeout(180);
  const layersAfterExportUndo = await page.locator(".art-layer").count();
  check("png.export-not-history", layersAfterExport === 3 && layersAfterExportUndo === 3, { layersAfterExport, layersAfterExportUndo });

  // Open the project saved above; opening resets history.
  const chooserPromise = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Open project file/ }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles(savedPath);
  await page.waitForTimeout(400);
  const importedLayers = await page.locator(".art-layer").count();
  await page.keyboard.press("Control+z");
  await page.waitForTimeout(180);
  const layersAfterOpenUndo = await page.locator(".art-layer").count();
  check("open.twin", importedLayers === 3 && layersAfterOpenUndo === 3, { importedLayers, layersAfterOpenUndo });

  // Print / Save PDF still isolates only the artboard.
  await page.evaluate(() => {
    window.print = () => {
      window.__twinPrintCalled = true;
    };
  });
  await page.getByRole("button", { name: "Open actions", exact: true }).click();
  await page.getByRole("button", { name: /Print \/ Save PDF/ }).click();
  const printClass = await page.locator("body").evaluate((body) => body.classList.contains("is-printing"));
  const printCalled = await page.evaluate(() => Boolean(window.__twinPrintCalled));
  await page.emulateMedia({ media: "print" });
  const printIsolation = await page.evaluate(() => ({
    artboardPosition: getComputedStyle(document.querySelector('[data-testid="artboard"]')).position,
    headerVisibility: getComputedStyle(document.querySelector(".twin-header")).visibility,
  }));
  check("print.artboard-only", printClass && printCalled && printIsolation.artboardPosition === "fixed" && printIsolation.headerVisibility === "hidden", {
    printClass,
    printCalled,
    printIsolation,
  });

  await context.close();
} catch (error) {
  check("probe.completed", false, String(error?.stack ?? error));
} finally {
  const uniquePageErrors = [...new Set(pageErrors)];
  const uniqueConsoleErrors = [...new Set(consoleErrors)];
  const uniqueExternal = [...new Set(externalRequests)];
  check("runtime.page-errors", uniquePageErrors.length === 0, uniquePageErrors);
  check("runtime.console-errors", uniqueConsoleErrors.length === 0, uniqueConsoleErrors);
  check("runtime.external-requests", uniqueExternal.length === 0, uniqueExternal);

  const summary = {
    baseUrl: BASE_URL,
    checkedAt: new Date().toISOString(),
    build: { js: scriptSource, css: styleSource },
    viewport: VIEWPORT,
    passed: checks.filter((item) => item.pass).length,
    failed: checks.filter((item) => !item.pass).length,
    checks,
    pageErrors: uniquePageErrors,
    consoleErrors: uniqueConsoleErrors,
    externalRequests: uniqueExternal,
  };
  await fs.writeFile(path.join(OUT, "live-followups.json"), JSON.stringify(summary, null, 2));
  console.log(`\n${summary.passed}/${checks.length} live follow-up checks passed; ${summary.failed} failed.`);
  await browser?.close();
  process.exitCode = summary.failed === 0 ? 0 : 1;
}
