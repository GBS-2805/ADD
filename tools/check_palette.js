// The rendered half of tools/check_palette.py: what a stylesheet cannot promise.
//
//   node tools/check_palette.js http://127.0.0.1:8799/
//
// Checks the active edition once:
//   * a pull-down paints anything but the page colour;
//   * a selected control fills with anything but the band, or inks it with
//     anything but the band's own ink;
//   * a disabled control is as bright as an enabled one;
//   * the status-bar meta, or the colour codes a page prints, disagree with the
//     edition in force;
//   * words on the band fall under 4.5:1;
//   * a page scrolls sideways.
const { chromium } = require("playwright");

const BASE = process.argv[2];
if (!BASE || !BASE.endsWith("/")) {
  console.error("usage: node tools/check_palette.js http://127.0.0.1:8799/  (ends in a slash)");
  process.exit(2);
}

const VIEWPORT = { width: 1300, height: 900 };

const SELECTORS = [
  ".bar", ".page-name", ".menu-btn", ".menu", ".drawer",
  ".panel-toggle", ".panel", ".panel-bar", "#search", ".cat-btn", ".cat-menu",
  ".cat-item[aria-selected='true']", ".export-btn", ".export-menu", ".export-item",
  ".seg", ".seg[aria-pressed='true']", ".sel-tab", ".sel-head", ".sel-btn",
  ".sel-chip", ".add-all", ".icon-card", ".preview", ".copy-btn", ".markup-btn",
  ".panel-foot", ".empty", "footer"
];

const failures = [];

function fail(message) {
  failures.push(message);
}

function parse(css) {
  const m = String(css).match(/[\d.]+/g);
  if (!m || m.length < 3) return null;
  const [r, g, b, a] = m.map(Number);
  if (a !== undefined && a <= 0.95) return null;
  return { r, g, b };
}

function luminance(css) {
  const c = parse(css);
  if (!c) return null;
  const f = (v) => {
    v /= 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
}

function hexToRgb(hex) {
  const digits = String(hex).replace("#", "");
  if (digits.length !== 6) return null;
  return "rgb(" + parseInt(digits.slice(0, 2), 16) + ", "
    + parseInt(digits.slice(2, 4), 16) + ", "
    + parseInt(digits.slice(4, 6), 16) + ")";
}

function contrast(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  if (la === null || lb === null) return null;
  const hi = Math.max(la, lb);
  const lo = Math.min(la, lb);
  return Number(((hi + 0.05) / (lo + 0.05)).toFixed(2));
}

async function open(browser, path) {
  const context = await browser.newContext({ viewport: VIEWPORT });
  const page = await context.newPage();
  page.on("pageerror", (e) => fail("pageerror: " + e.message));
  page.on("console", (m) => {
    if (m.type() === "error") fail("console: " + m.text());
  });
  await page.goto(BASE + path, { waitUntil: "networkidle" });
  return { context, page };
}

function readSurfaces(page) {
  return page.evaluate((selectors) => {
    const style = (sel, prop) => {
      const node = document.querySelector(sel);
      return node ? getComputedStyle(node)[prop] : "missing";
    };
    const root = getComputedStyle(document.documentElement);
    const values = {};
    for (const sel of selectors) {
      values[sel] = {};
      for (const prop of ["color", "backgroundColor", "borderTopColor", "borderRightColor"]) {
        values[sel][prop] = style(sel, prop);
      }
    }
    const meta = document.querySelector('meta[name="theme-color"]');
    const probe = document.createElement("div");
    probe.style.background = "var(--band)";
    probe.style.color = "var(--band-ink)";
    document.body.appendChild(probe);
    const band = getComputedStyle(probe).backgroundColor;
    const bandWords = getComputedStyle(probe).color;
    probe.remove();
    return {
      meta: meta ? meta.getAttribute("content") : "missing",
      band,
      bandWords,
      selected: style(".seg[aria-pressed='true']", "backgroundColor"),
      selectedInk: style(".seg[aria-pressed='true']", "color"),
      disabled: style(".sel-btn[disabled]", "color"),
      enabled: style(".add-all", "color"),
      menu: style(".menu", "backgroundColor"),
      drawer: style(".drawer", "backgroundColor"),
      overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth
    };
  }, SELECTORS);
}

(async () => {
  const browser = await chromium.launch();
  const { context, page } = await open(browser, "icons/coolicons/");

  // The panel is open on this page; the old toggle remains in the DOM, hidden.
  // Only click an opener the reader can actually see, or the check stalls on
  // an invisible control before it reads a single colour.
  const opener = await page.$('[aria-controls="panel"]');
  if (opener && await opener.isVisible()) {
    await opener.click();
    await page.waitForTimeout(400);
  }
  const cat = await page.$("#catBtn");
  if (cat) {
    await cat.click();
    await page.waitForTimeout(250);
  }
  const surfaces = await readSurfaces(page);
  const catMenu = await page.evaluate(() => {
    const node = document.querySelector("#catMenu");
    return node ? getComputedStyle(node).backgroundColor : "missing";
  });

  const format = await page.$("#formatBtn");
  if (format) {
    await format.click();
    await page.waitForTimeout(250);
  }
  const formatMenu = await page.evaluate(() => {
    const node = document.querySelector("#formats");
    return node ? getComputedStyle(node).backgroundColor : "missing";
  });

  const pageSurface = await page.evaluate(() => {
    const probe = document.createElement("div");
    probe.style.background = "var(--page)";
    document.body.appendChild(probe);
    const value = getComputedStyle(probe).backgroundColor;
    probe.remove();
    return value;
  });

  if (surfaces.menu !== pageSurface) {
    fail("the sections pull-down paints " + surfaces.menu + ", not the page " + pageSurface);
  }
  if (surfaces.drawer !== pageSurface) {
    fail("the side tab paints " + surfaces.drawer + ", not the page " + pageSurface);
  }
  if (catMenu !== pageSurface) {
    fail("the category pull-down paints " + catMenu + ", not the page " + pageSurface);
  }
  if (formatMenu !== pageSurface) {
    fail("the format pull-down paints " + formatMenu + ", not the page " + pageSurface);
  }
  if (surfaces.selected !== surfaces.band) {
    fail("a selected control fills " + surfaces.selected + ", not the band " + surfaces.band);
  }
  if (surfaces.selectedInk !== surfaces.bandWords) {
    fail("a selected control inks " + surfaces.selectedInk
      + ", not the band's own " + surfaces.bandWords);
  }
  if (hexToRgb(surfaces.meta) !== pageSurface) {
    fail("theme-color is " + surfaces.meta + ", not the page " + pageSurface);
  }
  if (surfaces.disabled === surfaces.enabled) {
    fail("a disabled control inks " + surfaces.disabled + ", the same as an enabled one");
  }
  const bandRatio = contrast(surfaces.bandWords, surfaces.band);
  if (bandRatio === null || bandRatio < 4.5) {
    fail("words on the band measure " + bandRatio + ":1, under 4.5:1");
  }
  if (surfaces.overflowX > 0) {
    fail("the page scrolls sideways by " + surfaces.overflowX + "px");
  }

  // What a page prints has to be what the edition is wearing.
  const codes = await page.evaluate(() => {
    const root = getComputedStyle(document.documentElement);
    return {
      printed: Array.from(document.querySelectorAll("[data-code]")).map((n) => ({
        key: n.getAttribute("data-code"),
        text: n.textContent.trim()
      })),
      live: ["band", "page", "on", "accent"].map((key) => ({
        key,
        value: root.getPropertyValue("--" + key).trim().toUpperCase()
      }))
    };
  });
  for (const slot of codes.printed) {
    const live = codes.live.find((entry) => entry.key === slot.key);
    if (live && slot.text.toUpperCase() !== live.value) {
      fail("the page prints " + slot.key + " " + slot.text
        + " while the edition is " + live.value);
    }
  }

  await context.close();
  await browser.close();

  if (failures.length) {
    console.error("palette (rendered): FAILED");
    for (const problem of failures) console.error("  " + problem);
    process.exit(1);
  }
  console.log("  palette (rendered): OK - one edition");
})().catch((error) => {
  console.error("palette (rendered): FAILED - " + error.message);
  process.exit(1);
});
