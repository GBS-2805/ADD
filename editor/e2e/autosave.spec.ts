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

/**
 * Waits for the app's own ready mark, not a storage poll.
 *
 * `data-editor-ready="true"` is set once the canvas has a real layout and the one
 * restore read has settled, which is exactly the precondition these tests need.
 * A poll on the stored document instead measured the 500 ms debounced write that
 * follows the restore, a later and unrelated event: on a loaded runner it could
 * miss a 5 s window while the app was already past the read (CI shard 1, run
 * 45325496262). The locator wait is bounded by the test's own timeout.
 */
async function waitForRestore(page: Page) {
  await page.locator('html[data-editor-ready="true"]').waitFor({ state: "attached" });
}

async function editHeadline(page: Page, value: string) {
  await page.getByRole("button", { name: "Inspector", exact: true }).click();
  const sheet = page.getByTestId("bottom-sheet-inspector");
  await sheet.locator("textarea").first().fill(value);
}

async function expectHeadline(page: Page, value: string) {
  await expect(page.getByTestId("art-layer-type-open-studio")).toContainText(value);
}

/**
 * Each test loads the app twice on a runner shared with the other shard's worker:
 * the first visit waits for Vite's cold transform and the reload for the restored
 * document and its paper shader. CI shard 1 (run 45334196027) measured 8.7 s to the
 * shell on the first load and a reloaded page that took more than 14 s to answer a
 * visibility check. The 30 s default is a render budget this spec cannot meet there;
 * the assertions keep their own waits, only the outer budget moves.
 */
test.describe.configure({ timeout: 90_000 });

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
