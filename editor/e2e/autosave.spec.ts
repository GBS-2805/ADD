import { expect, test, type Page } from "@playwright/test";

/**
 * Autosave against a browser's real IndexedDB, which jsdom does not have.
 *
 * Each test gets its own Playwright context, so its IndexedDB starts empty and
 * one test's document cannot seed another's. The viewport is a phone: the
 * hide/reload cycle is what a phone does, and `pagehide` is dispatched directly
 * rather than waiting for the browser to close a tab.
 */
const VIEWPORT = { width: 390, height: 844 };
const EDITED = "AUTOSAVED";

async function openEditor(page: Page) {
  await page.setViewportSize(VIEWPORT);
  await page.goto("/");
  await expect(page.getByTestId("editor-shell")).toBeVisible();
}

/**
 * Reads the stored text for the starter headline without the app's help.
 *
 * The upgrade handler matters: opening an existing database at its current version
 * does not run it, and opening a missing one without it would create an empty
 * database that the app's own open at the same version could never upgrade.
 */
async function storedText(page: Page): Promise<string | null> {
  return page.evaluate(async () => {
    const db = await new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open("bmo-editor", 1);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains("document")) database.createObjectStore("document");
        if (!database.objectStoreNames.contains("assets")) database.createObjectStore("assets");
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error ?? new Error("IndexedDB open failed."));
    });
    try {
      const payload = await new Promise<{
        document?: { layers?: Array<{ id?: string; text?: { content?: string } }> };
      } | undefined>((resolve, reject) => {
        const request = db.transaction("document", "readonly").objectStore("document").get("current");
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error("IndexedDB read failed."));
      });
      return payload?.document?.layers?.find((layer) => layer.id === "type-open-studio")?.text?.content ?? null;
    } finally {
      db.close();
    }
  });
}

/** Waits for the first visit's starter document to land, so the restore has settled. */
async function waitForRestore(page: Page) {
  await expect.poll(() => storedText(page)).not.toBeNull();
}

async function editHeadline(page: Page, value: string) {
  await page.getByRole("button", { name: "Inspector", exact: true }).click();
  const sheet = page.getByTestId("bottom-sheet-inspector");
  await sheet.locator("textarea").first().fill(value);
}

async function expectHeadline(page: Page, value: string) {
  await expect(page.getByTestId("art-layer-type-open-studio")).toContainText(value);
}

test("restores an edited text layer after a reload", async ({ page }) => {
  await openEditor(page);
  await waitForRestore(page);
  await editHeadline(page, EDITED);
  // Poll what storage holds rather than sleeping through the 500 ms debounce.
  await expect.poll(() => storedText(page)).toBe(EDITED);

  await page.reload();
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expectHeadline(page, EDITED);
});

test("writes the last edit when the page hides before the debounce", async ({ page }) => {
  await openEditor(page);
  await waitForRestore(page);
  await editHeadline(page, EDITED);
  // No timer advance: the hide itself has to write the edit.
  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  await expect.poll(() => storedText(page)).toBe(EDITED);

  await page.reload();
  await expect(page.getByTestId("editor-shell")).toBeVisible();
  await expectHeadline(page, EDITED);
});
