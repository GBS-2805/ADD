#!/usr/bin/env node
/**
 * Renders docs/assets/logo.svg to tools/logo-mark.png: the same mark, solid
 * black on a transparent background, so a compositor can tint it instead of
 * redrawing the curves.
 *
 *   node tools/render_logo_mark.js
 */
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const ROOT = path.dirname(__dirname);
const SIZE = 512;                       // square-ish: the artwork is 120.6 x 123.11
const HEIGHT = Math.round(SIZE * (123.11 / 120.6));

(async () => {
  const svg = fs.readFileSync(path.join(ROOT, "docs", "assets", "logo.svg"), "utf8");
  const browser = await chromium.launch();
  const page = await browser.newPage({
    viewport: { width: SIZE, height: HEIGHT },
    deviceScaleFactor: 1,
  });
  await page.setContent(
    `<style>html,body{margin:0;background:transparent}svg{display:block;` +
      `width:${SIZE}px;height:${HEIGHT}px;fill:#000}</style>${svg}`
  );
  const out = path.join(ROOT, "tools", "logo-mark.png");
  await page.screenshot({ path: out, omitBackground: true });
  await browser.close();
  console.log("wrote", path.relative(ROOT, out), `${SIZE}x${HEIGHT}`);
})();
