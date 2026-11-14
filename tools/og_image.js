// Render the stable 1200x630 social card. Not a page screenshot: a wordmark, one
// line and the paper/ink/plum palette, so social previews do not churn with the site.
//   node tools/og_image.js [docs/assets/og.png]
// Needs the render toolchain from tools/render_env.py.
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const out = path.resolve(process.argv[2] || "docs/assets/og.png");

const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><style>
  * { box-sizing: border-box; }
  html, body { width: 1200px; height: 630px; margin: 0; }
  body {
    display: flex; flex-direction: column; justify-content: space-between;
    padding: 76px 84px 64px;
    background: #f0e7d4; color: #15120e;
    font-family: Georgia, "Times New Roman", serif;
  }
  .rule { height: 6px; background: #15120e; }
  .mark { margin-top: 44px; font-size: 240px; line-height: .82; letter-spacing: -.06em; }
  .mark em { color: #c53a1d; font-style: italic; }
  .line { max-width: 900px; margin: 0; font-size: 42px; line-height: 1.15; color: #4c463a; }
  .foot { display: flex; justify-content: space-between; align-items: baseline;
    border-top: 1px solid rgba(21,18,14,.3); padding-top: 20px;
    font-family: ui-monospace, Menlo, monospace; font-size: 20px; letter-spacing: .08em;
    text-transform: uppercase; }
  .foot b { color: #c53a1d; }
</style></head>
<body>
  <div class="rule"></div>
  <div>
    <div class="mark">A<em>D</em>D</div>
    <p class="line">4,373 icons. Three type families. One offline Android app. No account.</p>
  </div>
  <div class="foot"><span>artbase-olive.vercel.app</span><b>Free design shelf</b></div>
</body></html>`;

(async () => {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  await page.setContent(html, { waitUntil: "load" });
  await page.screenshot({ path: out, type: "png" });
  await browser.close();
  const bytes = fs.statSync(out).size;
  console.log(`og: ${out} 1200x630 ${bytes} B`);
})().catch((error) => { console.error("FAIL:", error.message); process.exit(1); });
