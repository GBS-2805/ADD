#!/usr/bin/env node
/**
 * The Chromium build the suite will launch, against the build the pin names.
 *
 *   npm run check:browser
 *
 * CI runs the browser jobs inside `mcr.microsoft.com/playwright:v1.57.0-noble`
 * instead of downloading Chromium, so no `playwright install` line names the
 * revision any more. This launches the browser the specs launch — the headless
 * shell for `@playwright/test` 1.57.0 — prints its version and fails when it is
 * not the one `playwright-core` pins. Every perf ceiling in the plan was recorded
 * on revision 1200 / 143.0.7499.4, so a different build here would move the
 * baseline rather than the product.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { chromium } from "playwright";

const EDITOR = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const pin = JSON.parse(
  fs.readFileSync(path.join(EDITOR, "node_modules/playwright-core/browsers.json"), "utf8"),
).browsers.find((browser) => browser.name === "chromium");

const browser = await chromium.launch();
const version = browser.version();
await browser.close();

console.log(`browsers path: ${process.env.PLAYWRIGHT_BROWSERS_PATH ?? "(Playwright's default cache)"}`);
console.log(`pinned: chromium ${pin.browserVersion} (revision ${pin.revision})`);
console.log(`launched: chromium ${version}`);
if (version !== pin.browserVersion) {
  console.error("the launched Chromium is not the pinned build");
  process.exit(1);
}
