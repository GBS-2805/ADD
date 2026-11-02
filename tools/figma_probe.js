// Load a Figma prototype in headless Chromium, log what it fetches, screenshot it.
//   node tools/figma_probe.js <url> <out.png> <width> <height> <scale> [waitMs]
const { chromium } = require("/tmp/pwshot/node_modules/playwright");

(async () => {
  const [url, out, w, h, scale = "1", wait = "14000"] = process.argv.slice(2);
  const browser = await chromium.launch({ args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const page = await browser.newPage({
    viewport: { width: Number(w), height: Number(h) },
    deviceScaleFactor: Number(scale),
    userAgent:
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  });
  const media = [];
  page.on("response", async (r) => {
    const ct = (r.headers()["content-type"] || "").split(";")[0];
    if (/^image\//.test(ct)) {
      const len = Number(r.headers()["content-length"] || 0);
      media.push({ ct, len, url: r.url().slice(0, 150) });
    }
  });
  const errors = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text().slice(0, 120)); });

  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.waitForTimeout(Number(wait));
  await page.screenshot({ path: out, fullPage: false });

  const canvasBox = await page.evaluate(() => {
    const c = document.querySelector("canvas");
    if (!c) return null;
    const r = c.getBoundingClientRect();
    return { w: Math.round(r.width), h: Math.round(r.height) };
  });
  console.log("shot:", out, `${w}x${h}@${scale}x`);
  console.log("canvas css box:", canvasBox);
  console.log("document height:", await page.evaluate(() => document.documentElement.scrollHeight));
  console.log("image responses:", media.length);
  for (const m of media.slice(0, 8)) console.log("  ", m.ct, m.len, m.url);
  console.log("console errors:", errors.length ? errors.slice(0, 4) : "none");
  await browser.close();
})().catch((e) => { console.error("FAIL:", e.message); process.exit(1); });
