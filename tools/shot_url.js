// Load a URL in headless Chromium and screenshot it, reporting console errors.
//   node tools/shot_url.js <url> <out.png> [width] [height] [waitMs]
const path = "playwright";   // resolved through NODE_PATH, like render_pages.js
const { chromium } = require(path);

(async () => {
  const [url, out, w = "1440", h = "900", wait = "12000"] = process.argv.slice(2);
  const browser = await chromium.launch({ args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const page = await browser.newPage({
    viewport: { width: Number(w), height: Number(h) },
    deviceScaleFactor: 1,
    userAgent:
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  });
  const errors = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text().slice(0, 160)); });
  page.on("pageerror", (e) => errors.push("pageerror: " + String(e.message).slice(0, 160)));
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.waitForTimeout(Number(wait));
  await page.screenshot({ path: out, fullPage: false });
  console.log("final url:", page.url());
  console.log("title:", await page.title());
  const text = await page.evaluate(() => (document.body ? document.body.innerText : ""));
  console.log("text:", text.replace(/\s+/g, " ").slice(0, 700));
  const frames = page.frames().length;
  console.log("frames:", frames, " canvas count:", await page.evaluate(() => document.querySelectorAll("canvas").length));
  console.log("console errors:", errors.length ? errors.slice(0, 6) : "none");
  await browser.close();
})().catch((e) => { console.error("FAIL:", e.message); process.exit(1); });
