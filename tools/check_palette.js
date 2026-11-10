// The rendered half of tools/check_palette.py: what a stylesheet cannot promise.
//
//   node tools/check_palette.js http://127.0.0.1:8799/
//
// Drives every palette in both modes and fails when
//   * a pull-down paints anything but the palette's page colour;
//   * a selected control fills with anything but the palette's band, or inkes it
//     with anything but the band's own ink;
//   * a disabled control is as bright as an enabled one;
//   * the status-bar meta, or the colour codes a page prints, disagree with the
//     palette in force;
//   * words on the band fall under 4.5:1;
//   * a colour stays identical across all palettes without being on the
//     allowlist below (a literal that never met the palette);
//   * a page scrolls sideways.
const { chromium } = require("playwright");

const BASE = process.argv[2];
if (!BASE || !BASE.endsWith("/")) {
  console.error("usage: node tools/check_palette.js http://127.0.0.1:8799/  (ends in a slash)");
  process.exit(2);
}

const PALETTES = ["green", "moss", "navy", "ash"];
const MODES = ["dark", "light"];
const VIEWPORT = { width: 1300, height: 900 };

// The preview stage is the glyph's own surface: black or white is the export
// choice, so its fill is meant to be the same in every palette.
const FIXED_OK = new Set([
  ".preview color", ".preview backgroundColor", ".preview borderTopColor"
]);

const SELECTORS = [
  ".bar", ".page-name", ".menu-btn", ".menu", ".drawer",
  ".panel-toggle", ".panel", ".panel-bar", "#search", ".cat-btn", ".cat-menu",
  ".cat-item[aria-selected='true']", ".export-btn", ".export-menu", ".export-item",
  ".seg", ".seg[aria-pressed='true']", ".sel-tab", ".sel-head", ".sel-btn",
  ".sel-chip", ".add-all", ".icon-card", ".preview", ".copy-btn", ".markup-btn",
  ".panel-foot", ".empty", "footer"
];
const PROPS = ["color", "backgroundColor", "borderTopColor", "borderRightColor"];
const NEUTRAL = new Set(["rgba(0, 0, 0, 0)", "transparent", ""]);

const failures = [];
const samples = {};

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

async function open(browser, palette, mode, path) {
  const context = await browser.newContext({ viewport: VIEWPORT });
  await context.addInitScript(([p, m]) => {
    try {
      localStorage.setItem("site:palette", p);
      localStorage.setItem("site:theme", m);
    } catch (e) { /* store blocked */ }
  }, [palette, mode]);
  const page = await context.newPage();
  page.on("pageerror", (e) => fail(palette + "/" + mode + ": " + e.message));
  page.on("console", (m) => {
    if (m.type() === "error") fail(palette + "/" + mode + " console: " + m.text());
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
    return {
      tokens: {
        page: root.getPropertyValue("--page").trim(),
        band: root.getPropertyValue("--band").trim(),
        bandInk: root.getPropertyValue("--band-ink").trim(),
        accentInk: root.getPropertyValue("--accent-ink").trim()
      },
      meta: meta ? meta.getAttribute("content") : "missing",
      band: style(".bar", "backgroundColor"),
      bandWords: style(".page-name", "color"),
      selected: style(".seg[aria-pressed='true']", "backgroundColor"),
      selectedInk: style(".seg[aria-pressed='true']", "color"),
      disabled: style(".sel-btn[disabled]", "color"),
      enabled: style(".add-all", "color"),
      menu: style(".menu", "backgroundColor"),
      drawer: style(".drawer", "backgroundColor"),
      overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      values
    };
  }, SELECTORS);
}

(async () => {
  const browser = await chromium.launch();

  for (const palette of PALETTES) {
    for (const mode of MODES) {
      const tag = palette + "/" + mode;
      const { context, page } = await open(browser, palette, mode, "icons/coolicons/");

      const opener = await page.$('[aria-controls="panel"]');
      if (opener) {
        await opener.click();
        await page.waitForTimeout(400);
      }
      const cat = await page.$("#catBtn");
      if (cat) {
        await cat.click();
        await page.waitForTimeout(250);
      }
      const withCat = await readSurfaces(page);
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

      samples[palette] = samples[palette] || {};
      samples[palette][mode] = withCat.values;

      const pageSurface = await page.evaluate(() => {
        const root = getComputedStyle(document.documentElement);
        const probe = document.createElement("div");
        probe.style.background = "var(--page)";
        document.body.appendChild(probe);
        const value = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return value;
      });

      if (withCat.menu !== pageSurface) {
        fail(tag + ": the sections pull-down paints " + withCat.menu + ", not the page " + pageSurface);
      }
      if (withCat.drawer !== pageSurface) {
        fail(tag + ": the side tab paints " + withCat.drawer + ", not the page " + pageSurface);
      }
      if (catMenu !== pageSurface) {
        fail(tag + ": the category pull-down paints " + catMenu + ", not the page " + pageSurface);
      }
      if (formatMenu !== pageSurface) {
        fail(tag + ": the format pull-down paints " + formatMenu + ", not the page " + pageSurface);
      }
      if (withCat.selected !== withCat.band) {
        fail(tag + ": a selected control fills " + withCat.selected + ", not the band " + withCat.band);
      }
      if (withCat.selectedInk !== withCat.bandWords) {
        fail(tag + ": a selected control inks " + withCat.selectedInk
          + ", not the band's own " + withCat.bandWords);
      }
      if (hexToRgb(withCat.meta) !== withCat.band) {
        fail(tag + ": theme-color is " + withCat.meta + ", not the band " + withCat.band);
      }
      if (withCat.disabled === withCat.enabled) {
        fail(tag + ": a disabled control inks " + withCat.disabled + ", the same as an enabled one");
      }
      const bandRatio = contrast(withCat.bandWords, withCat.band);
      if (bandRatio === null || bandRatio < 4.5) {
        fail(tag + ": words on the band measure " + bandRatio + ":1, under 4.5:1");
      }
      if (withCat.overflowX > 0) {
        fail(tag + ": the page scrolls sideways by " + withCat.overflowX + "px");
      }

      await context.close();
    }
  }

  // What a page prints has to be what the palette is wearing.
  for (const palette of PALETTES) {
    const { context, page } = await open(browser, palette, "dark", "");
    const codes = await page.evaluate(() => {
      const root = getComputedStyle(document.documentElement);
      return {
        printed: Array.from(document.querySelectorAll("[data-code]")).map((n) => ({
          key: n.getAttribute("data-code"),
          text: n.textContent.trim()
        })),
        live: ["band", "page", "on", "accent"].map((key) => ({
          key,
          value: root.getPropertyValue("--pal-" + key).trim().toUpperCase()
        }))
      };
    });
    for (const slot of codes.printed) {
      const live = codes.live.find((entry) => entry.key === slot.key);
      if (live && slot.text.toUpperCase() !== live.value) {
        fail(palette + ": the page prints " + slot.key + " " + slot.text
          + " while the palette is " + live.value);
      }
    }
    await context.close();
  }

  // Anything identical in all palettes never met the palette.
  for (const sel of SELECTORS) {
    for (const prop of PROPS) {
      const seen = PALETTES.map((palette) => samples[palette].dark[sel][prop]);
      const unique = Array.from(new Set(seen));
      if (unique.length === 1 && !NEUTRAL.has(unique[0]) && unique[0] !== "missing") {
        if (!FIXED_OK.has(sel + " " + prop)) {
          fail("fixed colour: " + sel + " " + prop + " is " + unique[0] + " in every palette");
        }
      }
    }
  }

  await browser.close();

  if (failures.length) {
    console.error("palette (rendered): FAILED");
    for (const problem of failures) console.error("  " + problem);
    process.exit(1);
  }
  console.log("  palette (rendered): OK - " + PALETTES.length + " palettes x " + MODES.length + " modes");
})().catch((error) => {
  console.error("palette (rendered): FAILED - " + error.message);
  process.exit(1);
});
