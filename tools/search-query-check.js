// Open the search page with a query and prove the query reached the filter.
//   python3 -m http.server 8792 --directory docs &
//   node tools/search-query-check.js http://127.0.0.1:8792/
const { chromium } = require("playwright");

const base = (process.argv[2] || "http://127.0.0.1:8792/").replace(/\/?$/, "/");

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.goto(base + "search/?q=icons", { waitUntil: "networkidle" });
  await page.waitForFunction(() => document.querySelectorAll("#hits li").length > 0);
  const result = await page.evaluate(() => ({
    query: document.querySelector("#q").value,
    count: document.querySelectorAll("#hits li").length,
    heading: document.querySelector("#count").textContent.trim(),
  }));
  await browser.close();
  if (errors.length) {
    throw new Error("console errors: " + errors.join("; "));
  }
  if (result.query !== "icons" || !result.count) {
    throw new Error("search did not apply the query: " + JSON.stringify(result));
  }
  console.log(`search: "${result.query}" -> ${result.count} results (${result.heading})`);
})().catch((error) => { console.error("FAIL:", error.message); process.exit(1); });
