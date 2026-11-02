#!/usr/bin/env node
// Sweeps both catalogs for closed subpaths small enough that a heavy stroke would
// swallow them, which is the case that left a pinhole in a glyph's dot.
//
// For every icon it splits the path data into subpaths, measures each closed one,
// and reports the smallest against the four weights the preview offers. A subpath
// whose box is no larger than the stroke is one `solidDots()` fills; listing them
// lets the artwork be spot-checked instead of trusted.
//
//   node tools/audit_dots.js [docs]
//
// Needs the render toolchain (PLAYWRIGHT_BROWSERS_PATH, NODE_PATH, the rootless
// libs) because getBBox is the only reliable measure of a path's own geometry.

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const root = path.resolve(process.argv[2] || "docs");
const WEIGHTS = [1, 1.5, 2, 2.5];

async function measure(browser, payload) {
  const page = await browser.newPage();
  await page.setContent("<!doctype html><meta charset='utf-8'><body></body>");
  const result = await page.evaluate(({ icons }) => {
    const split = (d) => d.match(/[Mm][^Mm]*/g) || [];
    const host = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    host.setAttribute("width", "0");
    host.setAttribute("height", "0");
    host.setAttribute("fill", "none");
    document.body.appendChild(host);
    const out = [];
    for (const icon of icons) {
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.innerHTML = icon.body;
      host.appendChild(svg);
      for (const node of svg.querySelectorAll("path")) {
        for (const part of split(node.getAttribute("d") || "")) {
          if (!/[Zz]\s*$/.test(part)) continue;          // open subpaths cannot invert
          const probe = node.cloneNode(false);
          probe.setAttribute("d", part);
          svg.appendChild(probe);
          let box = null;
          try { box = probe.getBBox(); } catch (error) { box = null; }
          svg.removeChild(probe);
          if (!box) continue;
          const small = Math.min(box.width, box.height);
          if (small > 0 && small < 8) {
            out.push({ name: icon.name, cat: icon.cat || "", size: +small.toFixed(2) });
          }
        }
      }
      host.removeChild(svg);
    }
    return out;
  }, { icons: payload.icons });
  await page.close();
  return result;
}

(async () => {
  const browser = await chromium.launch();
  const report = { generated: new Date().toISOString().slice(0, 10), brands: {} };
  for (const [brand, file] of [["Lineicons", "icons.json"], ["Coolicons", "coolicons.json"]]) {
    const payload = JSON.parse(fs.readFileSync(path.join(root, "data", file), "utf8"));
    const dots = await measure(browser, payload);
    const glyphs = new Set(dots.map((dot) => dot.name));
    const filledAt = {};
    for (const weight of WEIGHTS) {
      filledAt[weight] = new Set(dots.filter((dot) => dot.size <= weight).map((dot) => dot.name)).size;
    }
    const smallest = dots.slice().sort((a, b) => a.size - b.size).slice(0, 8);
    report.brands[brand] = { glyphs: payload.count, withDots: glyphs.size, dots: dots.length, filledAt, smallest };
    console.log(`${brand}: ${payload.count} glyphs, ${glyphs.size} carry a dot, ${dots.length} dots total`);
    console.log(`   swallowed by weight -> 1:${filledAt[1]}  1.5:${filledAt[1.5]}  2:${filledAt[2]}  2.5:${filledAt[2.5]}`);
    for (const dot of smallest.slice(0, 5)) {
      console.log(`   ${dot.name.padEnd(26)} ${String(dot.size).padStart(5)} of 24 (${dot.cat || "uncategorised"})`);
    }
  }
  await browser.close();
  fs.mkdirSync("artifacts", { recursive: true });
  fs.writeFileSync(path.join("artifacts", "dots-audit.json"), JSON.stringify(report, null, 2));
  console.log("wrote artifacts/dots-audit.json");
})();
