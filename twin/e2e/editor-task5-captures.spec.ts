import { expect, test, type Page } from "@playwright/test";

const CAPTURES = "../artifacts/twin/editor";

async function open(page: Page, width: number, height: number) {
  await page.setViewportSize({ width, height });
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
}

async function capture(page: Page, name: string) {
  await page.waitForTimeout(250);
  await page.screenshot({ path: `${CAPTURES}/${name}.png`, animations: "disabled" });
}

test("captureFinal1440", async ({ page }) => {
  await open(page, 1440, 900);
  await capture(page, "final-1440");
});

test("captureFinal1024", async ({ page }) => {
  await open(page, 1024, 768);
  await capture(page, "final-1024");
});

test("captureFinal768", async ({ page }) => {
  await open(page, 768, 1024);
  await capture(page, "final-768");
});

test("captureFinal390", async ({ page }) => {
  await open(page, 390, 844);
  await capture(page, "final-390");
});

test("captureFinal360", async ({ page }) => {
  await open(page, 360, 840);
  await capture(page, "final-360");
});

test("captureFinalCover349", async ({ page }) => {
  await open(page, 349, 316);
  await capture(page, "final-cover-349");
});

test("captureFinalCover320", async ({ page }) => {
  await open(page, 320, 320);
  await capture(page, "final-cover-320");
});

test("captureFinalLandscape", async ({ page }) => {
  await open(page, 840, 360);
  await capture(page, "final-landscape-840x360");
});

test("captureFinalFold", async ({ page }) => {
  await page.addInitScript(() => {
    (window as unknown as { viewport: unknown }).viewport = {
      segments: [
        { x: 0, y: 0, width: 360, height: 420 },
        { x: 0, y: 420, width: 360, height: 420 },
      ],
    };
  });
  await open(page, 360, 840);
  await capture(page, "final-fold-360x840");
});

test("captureFinalPreview", async ({ page }) => {
  await open(page, 1440, 900);
  await page.getByTestId("preview-trigger").click();
  await expect(page.getByTestId("device-frame-compact")).toBeVisible();
  await capture(page, "final-preview");
});
