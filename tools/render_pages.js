// Capture the static site in headless Chromium.
//   node tools/render_pages.js <baseUrl> <outDir>
// Needs the toolchain from tools/render_env.py, served over python3 -m http.server.
const { chromium } = require("/tmp/pwshot/node_modules/playwright");

const base = process.argv[2] || "http://127.0.0.1:8765/";
const out = process.argv[3] || "artifacts";

const shots = [
  { name: "page-home-light", path: "", w: 1300, h: 900, full: false },
  { name: "page-home-full", path: "", w: 1300, h: 900, full: true },
  { name: "phone-home-light", path: "", w: 390, h: 844, full: false },
  { name: "phone-home-full", path: "", w: 390, h: 844, full: true },
  { name: "page-home-menu", path: "", w: 1300, h: 900, full: false, openMenu: true },
];

(async () => {
  const browser = await chromium.launch({ args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const errors = [];
  for (const s of shots) {
    const page = await browser.newPage({
      viewport: { width: s.w, height: s.h },
      deviceScaleFactor: 2,
    });
    page.on("console", (m) => { if (m.type() === "error") errors.push(`${s.name}: ${m.text().slice(0, 140)}`); });
    page.on("pageerror", (e) => errors.push(`${s.name}: pageerror ${String(e.message).slice(0, 140)}`));
    await page.goto(base + s.path, { waitUntil: "networkidle", timeout: 45000 });
    await page.evaluate(() => document.fonts.ready);
    if (s.openMenu) {
      await page.click("#burger");
      await page.waitForTimeout(200);
    }
    await page.screenshot({ path: `${out}/${s.name}.png`, fullPage: s.full });
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const fam = await page.evaluate(() => getComputedStyle(document.body).fontFamily);
    const loaded = await page.evaluate(() => document.fonts.check('17px Poppins'));
    const size = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.scrollHeight]);
    console.log(`${s.name}: bg ${bg} | ${fam} | poppins ${loaded} | doc ${size.join("x")}`);
    await page.close();
  }
  console.log("console errors:", errors.length ? errors : "none");
  await browser.close();
})().catch((e) => { console.error("FAIL:", e.message); process.exit(1); });
