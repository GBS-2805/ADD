#!/usr/bin/env node
/*
 * Report-only browser timing for the static pages.
 *
 *   NODE_PATH=/tmp/pwshot/node_modules PLAYWRIGHT_BROWSERS_PATH=/tmp/pw \
 *   node tools/check_web_perf.js --url http://127.0.0.1:8799/ --json
 *
 * Runs the home, icons, download and renders pages three times at phone width with
 * reduced motion. The result is evidence, not a blocking budget: shared-runner
 * timings are noisy and the plan keeps this metric report-only.
 */
"use strict";

const { chromium } = require("playwright");

const args = process.argv.slice(2);
const value = (flag, fallback) => {
  const index = args.indexOf(flag);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const base = value("--url", "http://127.0.0.1:8799/").replace(/\/?$/, "/");
const runs = Number(value("--runs", "3"));
const jsonOnly = args.includes("--json");
const pages = ["", "icons/", "download/", "renders/"];

const median = (values) => {
  const sorted = [...values].sort((a, b) => a - b);
  if (!sorted.length) return 0;
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
};

async function measure(browser, path) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: "reduce",
  });
  const page = await context.newPage();
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text().slice(0, 160));
  });
  page.on("pageerror", (error) => errors.push(String(error.message).slice(0, 160)));
  await page.goto(base + path, { waitUntil: "networkidle", timeout: 45000 });
  const metrics = await page.evaluate(async () => {
    const navigation = performance.getEntriesByType("navigation")[0] || {};
    const resources = performance.getEntriesByType("resource");
    let lcp = 0;
    try {
      const observer = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) lcp = entry.startTime;
      });
      observer.observe({ type: "largest-contentful-paint", buffered: true });
    } catch (error) { /* browser without LCP */ }
    let cls = 0;
    try {
      const observer = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (!entry.hadRecentInput) cls += entry.value;
        }
      });
      observer.observe({ type: "layout-shift", buffered: true });
    } catch (error) { /* browser without CLS */ }
    await new Promise((resolve) => setTimeout(resolve, 900));
    const transfer = resources.reduce((sum, entry) => sum + (entry.transferSize || 0),
                                      navigation.transferSize || 0);
    return {
      lcp: Math.round(lcp),
      cls: Number(cls.toFixed(4)),
      domContentLoaded: Math.round(navigation.domContentLoadedEventEnd || 0),
      transfer,
    };
  });
  await context.close();
  return { ...metrics, consoleErrors: errors };
}

(async () => {
  const browser = await chromium.launch({ args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const report = { url: base, runs, pages: {} };
  try {
    for (const path of pages) {
      const samples = [];
      for (let index = 0; index < runs; index += 1) {
        samples.push(await measure(browser, path));
      }
      report.pages[path || "/"] = {
        samples,
        median: {
          lcp: Math.round(median(samples.map((sample) => sample.lcp))),
          cls: Number(median(samples.map((sample) => sample.cls)).toFixed(4)),
          domContentLoaded: Math.round(median(samples.map((sample) => sample.domContentLoaded))),
          transfer: Math.round(median(samples.map((sample) => sample.transfer))),
        },
        consoleErrors: samples.reduce((all, sample) => all.concat(sample.consoleErrors), []),
      };
    }
  } finally {
    await browser.close();
  }
  if (jsonOnly) {
    process.stdout.write(JSON.stringify(report, null, 2) + "\n");
  } else {
    for (const [path, result] of Object.entries(report.pages)) {
      console.log(`${path}: LCP ${result.median.lcp}ms, CLS ${result.median.cls}, ` +
        `DCL ${result.median.domContentLoaded}ms, transfer ${result.median.transfer} B`);
    }
    console.log(`web-perf: OK - ${Object.keys(report.pages).length} pages, ${runs} runs each`);
  }
})().catch((error) => {
  console.error(`web-perf: ${error.message}`);
  process.exit(1);
});
