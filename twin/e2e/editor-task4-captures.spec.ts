import { expect, test, type Page } from "@playwright/test";

const CAPTURES = "../artifacts/twin/editor";

test.describe.configure({ timeout: 90_000 });

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

test("capturePreview411", async ({ page }) => {
  await open(page, 1440, 900);
  await page.getByTestId("preview-trigger").click();
  await expect(page.getByTestId("device-frame-compact")).toBeVisible();
  await capture(page, "preview-411");
});

test("capturePreview840", async ({ page }) => {
  await open(page, 1440, 900);
  await page.getByTestId("preview-trigger").click();
  await page.getByText("Expanded 840", { exact: true }).click();
  await expect(page.getByTestId("device-frame-expanded")).toBeVisible();
  await capture(page, "preview-840");
});

test("capturePhone360", async ({ page }) => {
  await open(page, 360, 840);
  await capture(page, "phone-360");
});

test("captureCover349", async ({ page }) => {
  await open(page, 349, 316);
  await capture(page, "cover-349");
});

test("captureCover320", async ({ page }) => {
  await open(page, 320, 320);
  await capture(page, "cover-320");
});

test("captureLandscape840x360", async ({ page }) => {
  await open(page, 840, 360);
  await capture(page, "landscape-840x360");
});

test("captureFold360x840", async ({ page }) => {
  await page.addInitScript(() => {
    (window as unknown as { viewport: unknown }).viewport = {
      segments: [
        { x: 0, y: 0, width: 360, height: 420 },
        { x: 0, y: 420, width: 360, height: 420 },
      ],
    };
  });
  await open(page, 360, 840);
  await capture(page, "fold-360x840");
});

test("captureEffects", async ({ page }) => {
  await open(page, 1440, 900);
  await page.getByRole("button", { name: "Effects", exact: true }).click();
  await expect(page.getByTestId("plugin-catalogue")).toBeVisible();
  await capture(page, "effects-catalogue");
  await page.getByTestId("catalogue-shader-lab").click();
  await page.getByTestId("shader-preset-paper").click();
  await expect(page.getByTestId("shader-preset-paper")).toHaveAttribute("aria-pressed", "true");
  await page.waitForTimeout(600);
  await capture(page, "effects-applied");
});
