// Capture the static site in headless Chromium, then write the gallery from the
// same list so the page can never list a file that is not here, or miss one that is.
//   node tools/render_pages.js <baseUrl> <outDir>
// Needs the toolchain from tools/render_env.py, served over python3 -m http.server.
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const base = process.argv[2] || "http://127.0.0.1:8765/";
const out = process.argv[3] || "artifacts";
const gallery = process.argv[4] || "docs/renders";

const shots = [
  { name: "page-home-light", page: "", w: 1300, h: 900, title: "Index", caption: "The landing page at its fold: the material, then two downloads." },
  { name: "page-home-full", page: "", w: 1300, h: 900, full: true, title: "Index, full height", caption: "The material, both apps and the palette." },
  { name: "page-menu", page: "", w: 1300, h: 900, open: "#menuBtn", title: "Left pull-down", caption: "Index, icons, fonts and tools, one row each." },
  { name: "page-drawer", page: "", w: 1300, h: 900, open: "#burger", title: "Right side tab", caption: "Download, portfolio and renders over the scrim." },
  { name: "page-tools", page: "tools/", w: 1300, h: 900, title: "Tools", caption: "Both apps, Icons 2.3 first, each with its own APK." },
  { name: "page-icons", page: "icons/", w: 1300, h: 900, title: "Icons app page", caption: "The icon brand landing: download, brands, what the app does." },
  { name: "page-icons-brand", page: "icons/coolicons/", w: 1300, h: 900, title: "Coolicons · preview", caption: "The preview tab with weight, size and background beside it." },
  { name: "page-icons-panel", page: "icons/coolicons/", w: 1300, h: 900, panel: true, scroll: "panel", title: "Coolicons · the set", caption: "All 1,258 icons behind one toggle, scrolling in their own sheet." },
  { name: "page-icons-variants", page: "icons/coolicons/", w: 1300, h: 900, panel: true, pick: 6, weight: "2.5", surface: "white", title: "Coolicons · variants", caption: "Heaviest line on the white tile: the same file, repainted live." },
  { name: "page-icons-categories", page: "icons/coolicons/", w: 1300, h: 900, panel: true, category: "System", scroll: "panel", title: "Coolicons · categories", caption: "The vendor's own 30 categories: System narrows the sheet to 102 icons." },
  { name: "page-all", page: "icons/all/", w: 1300, h: 900, full: true, title: "All brands \u00b7 one per category", caption: "Both brands, each sheet opening with the same eight categories." },
  { name: "page-lineicons-categories", page: "icons/lineicons/", w: 1300, h: 900, panel: true, category: "Brands & Logos", scroll: "panel", title: "Lineicons · categories", caption: "The other brand's own 51 categories: Brands & Logos holds 244 of its 606 icons." },
  { name: "page-fonts", page: "fonts/", w: 1300, h: 900, title: "Fonts", caption: "Poppins 400, 500 and 600 with specimens and downloads." },
  { name: "page-portfolio", page: "portfolio/", w: 1300, h: 900, title: "Portfolio", caption: "The apps, the browser and the site, with what was built in each." },
  { name: "page-download", page: "download/", w: 1300, h: 900, title: "Download", caption: "Both APKs, their measured sizes and both SHA-256 hashes." },
  { name: "phone-icons-sheet", page: "icons/coolicons/", w: 390, h: 844, scroll: "sheet", title: "Coolicons · shut sheet", caption: "Shut, the set is one complete row with a closed edge." },
  { name: "phone-lineicons-sheet", page: "icons/lineicons/", w: 390, h: 844, panel: true, category: "Arrows", scroll: "panel", title: "Lineicons · categories on a phone", caption: "The same picker on the other brand, stacked under the name filter at 390 wide." },
  { name: "phone-home-light", page: "", w: 390, h: 844, title: "Index on a phone", caption: "390×844: the band, the lede and both downloads." },
  { name: "phone-home-full", page: "", w: 390, h: 844, full: true, title: "Index, phone, full height", caption: "The whole landing page at phone width." },
  { name: "phone-menu", page: "", w: 390, h: 844, open: "#menuBtn", title: "Phone pull-down", caption: "Four rows under the band, counts on the right." },
  { name: "phone-drawer", page: "", w: 390, h: 844, open: "#burger", title: "Phone side tab", caption: "The tab takes 86% of the width so the page stays behind it." },
  { name: "phone-icons", page: "icons/", w: 390, h: 844, title: "Icons on a phone", caption: "The app page at phone width." },
  { name: "phone-icons-brand", page: "icons/coolicons/", w: 390, h: 844, panel: true, pick: 2, scroll: "stage", title: "Coolicons on a phone", caption: "Preview, variant rows, and the set opened below." },
  { name: "phone-all", page: "icons/all/", w: 390, h: 844, full: true, title: "All brands on a phone", caption: "Both sheets stacked at 390 wide, four across." },
  { name: "phone-all-coolicons", page: "icons/all/", w: 390, h: 844, scroll: "pane-coolicons", title: "All brands \u00b7 the second brand", caption: "Coolicons, one scroll under Lineicons." },
];

// Rendered apart from a page, so the gallery names it but the loop does not shoot it.
const extras = [
  { name: "logo-light", title: "The mark", caption: "The 45px logo at 4×, straight from assets/logo.svg." },
];

const escape = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");

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
    await page.goto(base + s.page, { waitUntil: "networkidle", timeout: 45000 });
    await page.evaluate(() => document.fonts.ready);
    if (s.open) {
      await page.click(s.open);
      await page.waitForTimeout(320);
    }
    if (s.panel) {
      await page.click("#panelToggle");
      await page.waitForTimeout(280);
    }
    if (s.pick !== undefined) {
      await page.locator(".icon-card").nth(s.pick).click();
      await page.waitForTimeout(160);
    }
    if (s.weight) {
      await page.click(`#weights .seg[data-weight="${s.weight}"]`);
      await page.waitForTimeout(120);
    }
    if (s.surface) {
      await page.click(`#surfaces .seg[data-surface="${s.surface}"]`);
      await page.waitForTimeout(120);
    }
    if (s.category) {
      await page.click("#catBtn");
      await page.waitForTimeout(120);
      await page.evaluate((name) => {
        const hit = Array.from(document.querySelectorAll(".cat-item"))
          .find((b) => b.textContent.trim().startsWith(name));
        if (hit) hit.click();
      }, s.category);
      await page.waitForTimeout(200);
    }
    if (s.scroll) {
      await page.evaluate((target) => {
        const el = target === "sheet" ? document.querySelector(".set-panel") : document.getElementById(target);
        if (el) el.scrollIntoView({ block: "start" });
      }, s.scroll);
      await page.waitForTimeout(200);
    }
    await page.screenshot({ path: `${out}/${s.name}.png`, fullPage: Boolean(s.full) });
    const info = await page.evaluate(() => {
      const menu = document.getElementById("menu");
      const drawer = document.getElementById("drawer");
      const panel = document.getElementById("panel");
      return {
        bg: getComputedStyle(document.body).backgroundColor,
        fam: getComputedStyle(document.body).fontFamily,
        poppins: document.fonts.check("17px Poppins"),
        hscroll: document.documentElement.scrollWidth > window.innerWidth + 1,
        menu: menu ? menu.getAttribute("data-open") : "none",
        drawer: drawer ? drawer.getAttribute("data-open") : "none",
        drawerVisible: drawer ? getComputedStyle(drawer).visibility : "none",
        panel: panel ? panel.getAttribute("data-open") : "none",
        cards: document.querySelectorAll(".icon-card").length,
        selected: (document.getElementById("selName") || {}).textContent || "",
        cat: (document.getElementById("catLabel") || {}).textContent || "",
        status: (document.getElementById("status") || {}).textContent || "",
        doc: [document.documentElement.scrollWidth, document.documentElement.scrollHeight],
      };
    });
    console.log(
      `${s.name}: bg ${info.bg} | ${info.fam.split(",")[0]} | poppins ${info.poppins}` +
      ` | hscroll ${info.hscroll} | menu ${info.menu} | drawer ${info.drawer}/${info.drawerVisible}` +
      ` | panel ${info.panel} | cards ${info.cards} | sel ${info.selected.slice(0, 18)}` +
      ` | ${info.status.slice(0, 46)} | doc ${info.doc.join("x")}`
    );
    await page.close();
  }
  console.log("console errors:", errors.length ? errors : "none");
  await browser.close();

  // Copy the shots in and rebuild the gallery, so the folder and the page agree.
  // The captures go in as lossless WebP: same pixels, about a third of the bytes,
  // which is what keeps the whole site inside the host's 10 MiB deploy budget.
  execFileSync("python3", [
    path.join(__dirname, "to_webp.py"), out, gallery,
    ...[...shots, ...extras].map((s) => s.name),
  ], { stdio: "inherit" });
  const kept = new Set([...shots, ...extras].map((s) => `${s.name}.webp`));
  for (const file of fs.readdirSync(gallery)) {
    if (/\.(png|webp)$/.test(file) && !kept.has(file)) {
      fs.unlinkSync(path.join(gallery, file));
      console.log("removed orphan", file);
    }
  }
  const GROUP_OF = { "": "Index", "tools/": "Tools", "icons/": "Icons app page",
    "icons/coolicons/": "Coolicons", "icons/lineicons/": "Lineicons",
    "icons/all/": "All brands", "fonts/": "Fonts",
    "portfolio/": "Portfolio", "download/": "Download" };
  const all = [...shots, ...extras];
  const groups = [];
  for (const s of all) {
    const label = s.name === "logo-light" ? "The mark" : (GROUP_OF[s.page] || s.page || "Other");
    let group = groups.find((g) => g.name === label);
    if (!group) { group = { name: label, shots: [] }; groups.push(group); }
    group.shots.push(s);
  }
  const card = (s) => `
      <figure>
        <h3>${escape(s.title)}</h3>
        <a href="${s.name}.webp"><img src="${s.name}.webp" alt="${escape(s.title)}" loading="lazy" /></a>
        <p class="cap">${escape(s.caption)}</p>
      </figure>`;
  const chips = [`<button class="chip" type="button" data-group="all" aria-pressed="true">All <span>${all.length}</span></button>`]
    .concat(groups.map((g) => `<button class="chip" type="button" data-group="${escape(g.name)}" aria-pressed="false">${escape(g.name)} <span>${g.shots.length}</span></button>`))
    .join("");
  const sections = groups.map((g) => `
    <section class="group" data-group="${escape(g.name)}">
      <h2>${escape(g.name)} <span>${g.shots.length} ${g.shots.length === 1 ? "capture" : "captures"}</span></h2>
      <div class="figs">${g.shots.map(card).join("")}
      </div>
    </section>`).join("");

  const page = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="theme-color" content="#3f4e96" />
<link rel="icon" href="../assets/favicon.svg" type="image/svg+xml" />
<title>Renders — __COUNT__ captures of this site</title>
<meta name="description" content="__COUNT__ screenshots of the shelf, captured in headless Chromium: the index at its fold, both pull-downs, the two apps, the icon brand pages with their previews and open sets, fonts, portfolio and download, at 1300 wide and 390 on a phone." />
<link rel="stylesheet" href="../assets/site.css" />
<style>
  .gal-filter { display: flex; flex-wrap: wrap; gap: 10px; margin: 20px 0 34px; }
  .chip { font: inherit; font-size: 14px; letter-spacing: .01em; padding: 9px 14px; color: var(--on); background: transparent; border: 1px solid var(--rule); cursor: pointer; }
  .chip span { margin-left: 8px; color: var(--on-dim); }
  .chip[aria-pressed="true"] { background: var(--band); color: var(--ink); border-color: var(--band); }
  .chip[aria-pressed="true"] span { color: var(--ink); }
  .chip:focus-visible { outline: 2px solid var(--band); outline-offset: -3px; }
  .group { margin: 0 0 56px; }
  .group > h2 { margin: 0 0 18px; font-size: 24px; font-weight: 500; }
  .group > h2 span { margin-left: 10px; font-size: 14px; color: var(--on-dim); }
  .group[hidden] { display: none; }
  .figs { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 34px; }
  figure { margin: 0; display: flex; flex-direction: column; border: 1px solid var(--rule); background: rgba(0, 0, 0, .16); }
  figure h3 { margin: 0; padding: 16px 18px 14px; font-size: 18px; font-weight: 500; line-height: 1.3; border-bottom: 1px solid var(--rule); }
  figure img { display: block; width: 100%; height: auto; border-bottom: 1px solid var(--rule); background: #2c3769; }
  .cap { margin: 0; padding: 14px 18px 18px; font-size: 13px; line-height: 1.55; color: var(--on-dim); }
  .wide { grid-column: 1 / -1; }
  @media (max-width: 700px) { .figs { grid-template-columns: 1fr; gap: 24px; } figure h3 { font-size: 17px; } }
</style>
</head>
<body>

<header class="bar">
  <div class="lead">
    <button class="menu-btn" id="menuBtn" type="button" aria-expanded="false" aria-controls="menu" aria-label="Open sections">
      <svg class="chev" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8 13L12 17L16 13M8 7L12 11L16 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </button>
    <a class="page-name" href="../">Renders</a>
  </div>

  <a class="mark" href="../" aria-label="Compress — home">
    <svg viewBox="0 0 120.6 123.11" role="img" aria-label="Compress logo" fill="currentColor">
      <path d="m29.58,61.92c.11,1.85.17,3.7.36,5.54.11,1.02-.23,1.23-1.19,1.29-4.47.26-5.38,1.22-5.27,5.67.13,5.14,2.74,9.13,6.27,12.56,1.53,1.49,2.96,1.05,3.89-.88.61-1.27.96-2.64,1.04-4.06.06-1.13.53-1.24,1.41-.69,1.72,1.08,2.92,2.71,4.38,4.07,8.66,8.04,18.38,13.89,30.58,14.51.52.03,1.04-.02,1.56,0,1.36.07,1.57-.53.89-1.58-.36-.56-.82-1.05-1.21-1.58-1.5-2.04-.63-4.17,1.89-4.53,3.34-.47,6.44.69,9.57,1.56,4.15,1.15,8.3,2.3,12.65,1.03,2.54-.74,2.78-1.66.84-3.54-.63-.61-1.41-1.08-2.11-1.63-2.43-1.91-2.63-3.35-.64-5.76,2.51-3.05,6.01-4.22,9.75-4.74,4.81-.67,9.26.31,13.25,3.19,1.39,1.01,1.75,1.97.94,3.7-9.48,20.25-25.12,32.69-47.2,36.28-29.25,4.75-57.41-12.11-67.54-39.86C-8.51,49.01,10.62,11.88,44.98,2.31c31.94-8.9,65.21,8.7,75.4,39.81.46,1.42.23,2.38-.66,3.4-4.28,4.87-9.78,6.13-15.92,5.16-4.99-.79-9.16-3.27-12.65-6.88-1.34-1.38-2.66-2.79-3.87-4.28-13.61-16.78-43.35-15.8-54.84,7.54-2.32,4.71-3.19,9.66-2.85,14.86Z"/>
      <path d="m80.89,57.02c.88-.09,2.06-.21,3.22.13,1.56.46,1.88,1.6.75,2.8-1.16,1.23-2.59,1.52-4.25,1.33-4.23-.48-5.14.47-4.58,4.78.43,3.35,1.88,6.19,4.52,8.36.99.81,1.67.93,2.18-.62,1.55-4.74,1.61-4.72,6.46-4.56,1.28.04,2.56-.04,3.49-1.05.76-.83,1.38-1.54,2.5-.44,1.07,1.05,1.29,2.15.42,3.35-1.18,1.61-2.84,2.29-4.82,2.23-1.25-.04-2.77-.51-3.68.03-.98.58-.39,2.29-.7,3.49-.75,2.89-2.94,3.94-5.77,2.62-5.71-2.66-10.17-13.01-8.12-18.87.86-2.45,2.86-3.21,5.21-3.41.96-.08,1.92-.11,3.18-.18Z"/>
      <path d="m52.62,58.41c.19-1.57.26-3.23,1.99-3.92,1.62-.65,2.9.23,3.92,1.33,2.41,2.61,3.79,5.64,2.86,9.26-.6,2.33-2.43,2.97-4.42,1.62-2.92-1.97-3.98-4.98-4.35-8.29Z"/>
      <path d="m44.97,66.78c-.03,1.9-.18,3.24-1.63,3.9-1.5.68-2.61-.25-3.53-1.19-2.06-2.11-3.15-4.68-2.7-7.66.4-2.62,2.46-3.4,4.53-1.73,2.25,1.82,3.29,4.3,3.33,6.68Z"/>
    </svg>
  </a>

  <div class="who">
    <div class="who-lines">
      <p>Compress</p>
      <p>__COUNT__ captures</p>
    </div>
    <button class="burger" id="burger" type="button" aria-expanded="false" aria-controls="drawer" aria-label="Open side tab">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 7H19M5 12H19M5 17H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </button>
  </div>
</header>

<nav class="menu" id="menu" data-open="false" aria-label="Sections">
  <a href="../">Index<span>shelf</span></a>
  <a href="../icons/">Icons<span>1,864 icons</span></a>
  <a href="../fonts/">Fonts<span>3 faces</span></a>
  <a href="../tools/">Tools<span>2 apps</span></a>
</nav>

<div class="scrim" id="scrim" data-open="false"></div>
<aside class="drawer" id="drawer" data-open="false" aria-label="More">
  <div class="drawer-head">
    <p>More</p>
    <button class="drawer-close" id="drawerClose" type="button" aria-label="Close side tab"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6L18 18M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
  </div>
  <a href="../download/">Download<span>2 apps</span></a>
  <a href="../portfolio/">Portfolio<span>3 projects</span></a>
  <a href="../renders/" aria-current="page">Renders<span>gallery</span></a>
  <p class="drawer-note">Every image here is a screenshot of a page that ran in a headless browser. Nothing is drawn by hand.</p>
</aside>

<main>
  <header class="page-head">
    <h1>Renders</h1>
    <p class="lede"><b>__COUNT__ captures</b>, all from one headless Chromium run against the built pages. Desktop at 1300×900, phones at 390×844, and nothing in the folder that this page does not list.</p>
    <p class="meta">
      <span>__COUNT__ images</span>
      <span>1300×900 and 390×844</span>
      <span>2× device scale</span>
      <span>No app screens</span>
    </p>
  </header>

  <section>
    <h2>The captures</h2>
    <p class="kicker">Grouped by page, in the order the run took them</p>
    <nav class="gal-filter" id="galFilter" aria-label="Filter captures by page">${chips}</nav>
    <div id="galGroups">${sections}
    </div>
    <p class="fine">The Android apps cannot be captured on this machine: <code>/dev/kvm</code> is unusable for this account, so the emulator runs under software emulation and every screencap comes back black. The gallery therefore carries no app screenshots rather than a drawing of one.</p>
  </section>
</main>

<footer>
  <span>__COUNT__ captures · <code>tools/render_pages.js</code></span>
  <span><a href="../">Index</a> · <a href="../tools/">Tools</a> · <a href="../download/">Download</a></span>
</footer>

<script>
(function () {
  var nav = document.getElementById("galFilter");
  if (!nav) { return; }
  nav.addEventListener("click", function (event) {
    var chip = event.target.closest(".chip");
    if (!chip) { return; }
    var want = chip.getAttribute("data-group");
    Array.prototype.forEach.call(nav.querySelectorAll(".chip"), function (button) {
      button.setAttribute("aria-pressed", button === chip ? "true" : "false");
    });
    Array.prototype.forEach.call(document.querySelectorAll(".group"), function (section) {
      section.hidden = want !== "all" && section.getAttribute("data-group") !== want;
    });
  });
})();
</script>
<script src="../assets/site.js" defer></script>
</body>
</html>
`.replace(/__COUNT__/g, String(shots.length + extras.length));

  fs.writeFileSync(path.join(gallery, "index.html"), page);
  console.log(`gallery: ${shots.length + extras.length} figures, ${shots.length} captured + ${extras.length} extra`);
})().catch((e) => { console.error("FAIL:", e.message); process.exit(1); });
