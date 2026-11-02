// Capture the static site in headless Chromium.
//   node tools/render_pages.js <baseUrl> <outDir>
// Needs the toolchain from tools/render_env.py, served over python3 -m http.server.
const { chromium } = require("playwright");

const base = process.argv[2] || "http://127.0.0.1:8765/";
const out = process.argv[3] || "artifacts";

const shots = [
  { name: "page-home-light", path: "", w: 1300, h: 900 },
  { name: "page-home-full", path: "", w: 1300, h: 900, full: true },
  { name: "page-menu", path: "", w: 1300, h: 900, open: "#menuBtn" },
  { name: "page-menu-right", path: "", w: 1300, h: 900, open: "#burger" },
  { name: "page-tools", path: "tools/", w: 1300, h: 900 },
  { name: "page-icons-light", path: "icons/", w: 1300, h: 900 },
  { name: "page-fonts", path: "fonts/", w: 1300, h: 900 },
  { name: "page-portfolio", path: "portfolio/", w: 1300, h: 900 },
  { name: "page-download", path: "download/", w: 1300, h: 900 },
  { name: "phone-home-light", path: "", w: 390, h: 844 },
  { name: "phone-home-full", path: "", w: 390, h: 844, full: true },
  { name: "phone-menu", path: "", w: 390, h: 844, open: "#menuBtn" },
  { name: "phone-icons-light", path: "icons/", w: 390, h: 844 },
  { name: "phone-download", path: "download/", w: 390, h: 844 },
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
    if (s.open) {
      await page.click(s.open);
      await page.waitForTimeout(220);
    }
    await page.screenshot({ path: `${out}/${s.name}.png`, fullPage: Boolean(s.full) });
    const info = await page.evaluate(() => ({
      bg: getComputedStyle(document.body).backgroundColor,
      fam: getComputedStyle(document.body).fontFamily,
      poppins: document.fonts.check("17px Poppins"),
      hscroll: document.documentElement.scrollWidth > window.innerWidth + 1,
      open: ["menu", "more"].filter((id) => !document.getElementById(id).hidden),
      doc: [document.documentElement.scrollWidth, document.documentElement.scrollHeight],
    }));
    console.log(
      `${s.name}: bg ${info.bg} | ${info.fam.split(",")[0]} | poppins ${info.poppins}` +
      ` | hscroll ${info.hscroll} | open [${info.open.join(",")}] | doc ${info.doc.join("x")}`
    );
    await page.close();
  }
  console.log("console errors:", errors.length ? errors : "none");
  await browser.close();
})().catch((e) => { console.error("FAIL:", e.message); process.exit(1); });
