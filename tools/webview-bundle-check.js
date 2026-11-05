// Drive the staged ADD web bundle the way the WebView loads it.
//
//   python3 -m http.server 8870 --directory app/assets/www
//   node tools/webview-bundle-check.js http://127.0.0.1:8870
//
// Proves the shipped HTML, CSS, JS and JSON work offline: every brand section
// fills, the fonts page renders its catalog and remembers a face, every menu link
// resolves inside the bundle, Poppins loads and nothing logs an error. It does not
// prove the native window or the JS bridge.
const { chromium } = require("playwright");
const BASE = (process.argv[2] || "http://127.0.0.1:8870").replace(/\/$/, "");

(async () => {
  const browser = await chromium.launch({ args: ["--no-sandbox"] });
  const errors = [];
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push("pageerror " + e.message));

  await page.goto(BASE + "/icons/all/", { waitUntil: "networkidle" });
  await page.waitForFunction(() => document.querySelectorAll(".cell").length >= 40, { timeout: 20000 });

  // A section fetches its sheet when it nears the viewport, so a phone-sized page
  // has to be scrolled before all four can be counted -- otherwise the last one
  // reads as twelve reserved cells with no glyphs.
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 500) {
      window.scrollTo(0, y);
      await new Promise((r) => setTimeout(r, 200));
    }
  });
  await page.waitForFunction(() => {
    const parts = Array.from(document.querySelectorAll(".sheet-body"));
    return parts.length === 4 && parts.every((p) => p.querySelectorAll(".cell svg").length === 12);
  }, { timeout: 20000 });

  const shape = await page.evaluate(() => ({
    sections: Array.from(document.querySelectorAll(".sheet-body")).map((part) => {
      const name = part.querySelector(".brand-name");
      return {
        brand: name ? (name.textContent.trim().match(/^[A-Za-z]+/) || ["?"])[0] : "?",
        cells: part.querySelectorAll(".cell").length,
        svgs: part.querySelectorAll(".cell svg").length,
        cats: new Set(Array.from(part.querySelectorAll(".cell [class*=cat]")).map((c) => c.textContent.trim())).size,
      };
    }),
    menu: Array.from(document.querySelectorAll("#menu a")).map((a) => new URL(a.getAttribute("href"), location.href).pathname),
    fonts: document.fonts.check("17px Poppins"),
  }));

  const statuses = {};
  const routes = new Set([...shape.menu, "/icons/", "/icons/coolicons/", "/icons/lineicons/", "/icons/phosphor/", "/icons/streamline/",
    "/fonts/", "/data/fonts.json",
    "/data/lineicons-sheet.json", "/data/coolicons-sheet.json", "/data/phosphor-sheet.json", "/data/streamline-sheet.json",
    "/data/streamline-line.json", "/data/streamline-duo.json", "/data/streamline-solid.json", "/data/streamline-flat.json",
    "/search/", "/data/pages.json", "/data/icons.json", "/data/icons/1.json", "/data/icons/4.json",
    "/data/coolicons/1.json", "/data/phosphor/1.json", "/data/streamline-line/1.json"]);
  for (const href of routes) {
    statuses[href] = (await page.request.get(BASE + href)).status();
  }

  await page.goto(BASE + "/icons/coolicons/", { waitUntil: "networkidle" });
  await page.click("#menuBtn");
  await page.waitForTimeout(280);
  const allHref = await page.evaluate(() => {
    const link = Array.from(document.querySelectorAll("#menu a")).find((a) => a.textContent.trim().startsWith("All"));
    return link ? new URL(link.getAttribute("href"), location.href).pathname : null;
  });
  statuses["coolicons All -> " + allHref] = allHref ? (await page.request.get(BASE + allHref)).status() : 0;

  // The bundle carries the search page too: it reads the staged page index and
  // filters by section. Drive it the way a thumb would -- chip, word, arrow keys.
  const search = { chips: [], total: 0, filtered: 0, arrows: false, escaped: false, opened: null, openedStatus: 0 };
  await page.goto(BASE + "/search/", { waitUntil: "networkidle" });
  await page.waitForFunction(() => document.querySelectorAll("#chips button").length > 1, { timeout: 20000 });
  search.total = await page.evaluate(() => document.querySelectorAll("#hits li").length);
  const chips = await page.evaluate(() => Array.from(document.querySelectorAll("#chips button")).map((button) => ({
    label: button.firstChild.textContent.trim(),
    n: Number(button.querySelector("span").textContent),
  })));
  for (let i = 1; i < chips.length; i++) {
    await page.click(`#chips button:nth-child(${i + 1})`);
    const hits = await page.evaluate(() => document.querySelectorAll("#hits li").length);
    search.chips.push(chips[i].label + ":" + (hits === chips[i].n ? hits : hits + "!=" + chips[i].n));
  }
  await page.click("#chips button:nth-child(1)");
  await page.fill("#q", "icons");
  search.filtered = await page.evaluate(() => document.querySelectorAll("#hits li").length);
  await page.press("#q", "ArrowDown");
  await page.press("#q", "ArrowDown");
  search.arrows = await page.evaluate(() => document.querySelectorAll('#hits a[data-active="true"]').length) === 1;
  await page.press("#q", "Escape");
  search.escaped = await page.evaluate((all) => document.querySelector("#q").value === ""
    && document.querySelectorAll("#hits li").length === all, chips[0].n);
  await page.fill("#q", "icons");
  await page.press("#q", "ArrowDown");
  await Promise.all([page.waitForNavigation(), page.press("#q", "Enter")]);
  search.opened = new URL(page.url()).pathname;
  search.openedStatus = (await page.request.get(BASE + search.opened)).status();
  const searchOk = search.total > 0 && search.chips.length >= 1
    && search.chips.every((entry) => !entry.includes("!="))
    && search.filtered > 0 && search.filtered <= search.total
    && search.arrows && search.openedStatus === 200;

  // The fonts page is the bundle's second tool: it fetches the staged catalog,
  // declares one face per style and remembers the chosen one. Drive a Try button and
  // reload, so the page is exercised rather than merely reachable.
  await page.goto(BASE + "/fonts/", { waitUntil: "networkidle" });
  await page.waitForFunction(() => document.querySelectorAll(".family").length >= 2, { timeout: 20000 });
  const catalog = await (await page.request.get(BASE + "/data/fonts.json")).json();
  const families = catalog.families || [];
  const fontPage = {
    sheets: 0, slots: 0, catalogFamilies: families.length, styles: catalog.styles || 0,
    picksOk: 0, woff2: 0, woff: 0, ttf: 0, woff2Missing: [], woffMissing: [], ttfMissing: [],
    saved: [], tried: null, remembered: false, restored: false, painted: false,
  };
  // Poppins is one sheet and Redaction is one sheet holding its seven grades, so the
  // sheet count is the number of family groups, not the number of catalog entries.
  // Every catalog family still owns a slot and its own style menu, and the menu has
  // to list exactly the styles that family has -- that is the assertion, not the count
  // of sheets.
  fontPage.sheets = await page.evaluate(() => document.querySelectorAll(".family").length);
  fontPage.picksOk = await page.evaluate((fams) => fams.filter((fam) => {
    const slot = document.querySelector('.face-slot[data-family="' + fam.family + '"]');
    const menu = slot && slot.parentElement.querySelector(".style-menu");
    return !!slot && !!menu && menu.querySelectorAll("li").length === fam.styles;
  }).length, families.map((fam) => ({ family: fam.family, styles: fam.styles.length })));
  fontPage.slots = await page.evaluate(() => document.querySelectorAll(".face-slot[data-family]").length);
  // Every woff2 and woff the catalog names has to be staged: the first is what the
  // page renders with, the second is the download the app writes to Downloads/Fonts.
  // The ttf links are a site-only format, so a missing ttf is counted rather than
  // failing -- the page says so when it is tapped in the app.
  for (const fam of families) {
    for (const face of fam.styles) {
      for (const kind of ["woff2", "woff", "ttf"]) {
        const url = face[kind];
        if (url && (await page.request.get(BASE + url)).status() === 200) fontPage[kind]++;
        else if (url) fontPage[kind + "Missing"].push(url);
      }
    }
  }
  // Poppins' sheet is driven the way a reader drives it: open the style pull-down,
  // take its last style, type into the hero box, and check that the sheet, the
  // specimen and the store all followed. The page used to do this with a row of Try
  // buttons; these assertions kept looking for them long after they were retired.
  await page.click('.family[data-family="Poppins"] .style-btn');
  fontPage.tried = await page.evaluate(() => {
    const items = document.querySelectorAll('.family[data-family="Poppins"] .style-menu li');
    const li = items[items.length - 1];
    return { family: li.dataset.family, weight: Number(li.dataset.weight),
      style: li.dataset.style, label: li.textContent.trim() };
  });
  await page.click('.family[data-family="Poppins"] .style-menu li:last-child');
  await page.fill("#tryText", "Handgloves 0123");
  const shownFace = await page.evaluate(() => {
    const specimen = document.querySelector('.face-slot[data-family="Poppins"] .specimen');
    const style = specimen ? getComputedStyle(specimen) : null;
    const label = document.querySelector('.family[data-family="Poppins"] .style-name');
    return {
      label: label ? label.textContent.trim() : "",
      family: style ? style.fontFamily : "",
      weight: style ? style.fontWeight : "",
      typed: Array.from(document.querySelectorAll(".face-slot .specimen"))
        .every((s) => s.textContent.trim() === "Handgloves 0123"),
    };
  });
  fontPage.painted = shownFace.family.includes("Poppins") && shownFace.typed
    && shownFace.label === fontPage.tried.label
    && Number(shownFace.weight) === fontPage.tried.weight;
  const stored = await page.evaluate(() => { try { return localStorage.getItem("fonts:preview"); } catch (e) { return null; } });
  fontPage.remembered = !!stored && stored.includes(fontPage.tried.family)
    && stored.includes(fontPage.tried.style) && stored.includes("Handgloves 0123");
  await page.reload({ waitUntil: "networkidle" });
  await page.waitForFunction(() => document.querySelectorAll(".face-slot").length >= 2, { timeout: 20000 });
  fontPage.restored = await page.evaluate((want) => {
    const label = document.querySelector('.family[data-family="Poppins"] .style-name');
    const field = document.querySelector("#tryText");
    return !!label && label.textContent.trim() === want
      && !!field && field.value === "Handgloves 0123";
  }, fontPage.tried.label);
  // The app has no download of its own, so the page hands a tapped font to the
  // bridge. Stand in for ShelfActivity's saveFont and tap both formats the bundle
  // carries, which also proves the tap does not navigate away from the sheet.
  await page.evaluate(() => {
    window.__saved = [];
    window.IconsApp = { saveFont: (href) => { window.__saved.push(href); } };
  });
  for (const kind of ["woff2", "woff"]) {
    await page.click(`.face-slot a[download][href$=".${kind}"]`, { noWaitAfter: true });
  }
  fontPage.saved = await page.evaluate(() => window.__saved.slice());
  const fontPageOk = fontPage.slots === fontPage.catalogFamilies && fontPage.catalogFamilies >= 2
    && fontPage.sheets >= 2 && fontPage.picksOk === fontPage.catalogFamilies
    && fontPage.remembered && fontPage.restored
    && fontPage.painted && fontPage.woff2Missing.length === 0 && fontPage.woffMissing.length === 0
    && fontPage.saved.length === 2
    && fontPage.saved[0].endsWith(".woff2") && fontPage.saved[1].endsWith(".woff");

  const sectionsOk = shape.sections.length === 4 && shape.sections.every((s) => s.cells === 12 && s.svgs === 12);
  const linksOk = Object.values(statuses).every((code) => code === 200);
  const ok = sectionsOk && linksOk && shape.fonts && searchOk && fontPageOk && errors.length === 0;

  console.log(JSON.stringify({ sections: shape.sections, fonts: shape.fonts, fontPage, statuses, search, errors, ok }, null, 2));
  console.log(ok ? "bundle: OK" : "bundle: FAILED");
  await browser.close();
  process.exit(ok ? 0 : 1);
})();
