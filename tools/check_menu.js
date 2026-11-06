// The shared band's keyboard contract: the page-name disclosure must start closed,
// and closing either panel must return focus to the control that opened it.
//
//   node tools/check_menu.js http://127.0.0.1:8799/
//
// This is the browser half of check_all.py --browser. It drives the root and a
// generated nested page at phone width, because both are built from the same chrome
// but only the generated page proves the template kept the attributes.
const { chromium } = require("playwright");

const BASE = process.argv[2];
if (!BASE || !BASE.endsWith("/")) {
  console.error("usage: node tools/check_menu.js http://127.0.0.1:8799/  (ends in a slash)");
  process.exit(2);
}

const SAMPLES = ["", "icons/coolicons/"];
const failures = [];

function fail(message) {
  failures.push(message);
}

function expect(condition, message) {
  if (!condition) fail(message);
}

async function readState(page) {
  return page.evaluate(() => {
    const get = (selector) => document.querySelector(selector);
    const active = document.activeElement;
    const name = get(".page-name");
    const describe = (node) => {
      if (!node) return "none";
      if (node.id) return "#" + node.id;
      if (node.classList && node.classList.contains("page-name")) return ".page-name";
      if (node.classList && node.classList.contains("burger")) return ".burger";
      return node.tagName.toLowerCase();
    };
    return {
      active: describe(active),
      nameRole: name ? name.getAttribute("role") : "missing",
      nameExpanded: name ? name.getAttribute("aria-expanded") : "missing",
      nameControls: name ? name.getAttribute("aria-controls") : "missing",
      menuExpanded: get("#menuBtn")?.getAttribute("aria-expanded") ?? "missing",
      menuOpen: get("#menu")?.getAttribute("data-open") ?? "missing",
      drawerExpanded: get("#burger")?.getAttribute("aria-expanded") ?? "missing",
      drawerOpen: get("#drawer")?.getAttribute("data-open") ?? "missing",
      markLabel: get(".bar .mark, header .mark")?.getAttribute("aria-label") ?? "missing",
      logoLabel: get(".bar .mark svg, header .mark svg")?.getAttribute("aria-label") ?? "missing"
    };
  });
}

async function openMenuWith(page, opener, expected, label) {
  await page.locator(opener).focus();
  await page.keyboard.press("Enter");
  await page.waitForFunction(
    () => document.querySelector("#menu")?.getAttribute("data-open") === "true",
    null, { timeout: 1500 }
  );
  const opened = await readState(page);
  expect(opened.nameExpanded === "true", label + ": page-name did not announce the open menu");
  expect(opened.menuExpanded === "true", label + ": #menuBtn did not announce the open menu");

  await page.locator("#menu a").first().focus();
  const inside = await page.evaluate(() => !!document.activeElement.closest("#menu"));
  expect(inside, label + ": a menu link could not take focus");

  await page.keyboard.press("Escape");
  await page.waitForFunction(
    () => document.querySelector("#menu")?.getAttribute("data-open") === "false",
    null, { timeout: 1500 }
  );
  const closed = await readState(page);
  expect(closed.nameExpanded === "false", label + ": page-name stayed expanded after Escape");
  expect(closed.menuExpanded === "false", label + ": #menuBtn stayed expanded after Escape");
  expect(closed.active === expected, label + ": Escape returned focus to " + closed.active
    + ", not " + expected);
}

async function checkPage(page, path) {
  const tag = path || "/";
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push("pageerror " + error.message));

  await page.goto(BASE + path, { waitUntil: "load" });
  await page.waitForFunction(() => {
    const name = document.querySelector(".page-name");
    return !!name && name.getAttribute("role") === "button";
  }, null, { timeout: 15000 });

  const initial = await readState(page);
  expect(initial.nameRole === "button", tag + ": page-name is not a button");
  expect(initial.nameExpanded === "false", tag + ": page-name did not start with aria-expanded=false");
  expect(initial.nameControls === "menu", tag + ": page-name does not control the sections menu");
  expect(initial.menuOpen === "false", tag + ": the sections menu started open");
  expect(initial.menuExpanded === "false", tag + ": #menuBtn started expanded");
  expect(initial.drawerOpen === "false", tag + ": the side tab started open");
  expect(initial.drawerExpanded === "false", tag + ": #burger started expanded");
  expect(initial.markLabel === "ADD — home", tag + ': mark reads "' + initial.markLabel + '"');
  expect(initial.logoLabel === "ADD logo", tag + ': logo reads "' + initial.logoLabel + '"');

  await openMenuWith(page, "#menuBtn", "#menuBtn", tag + " chevron");
  await openMenuWith(page, ".page-name", ".page-name", tag + " page name");

  await page.locator("#burger").click();
  await page.waitForFunction(
    () => document.querySelector("#drawer")?.getAttribute("data-open") === "true",
    null, { timeout: 1500 }
  );
  const drawerOpened = await readState(page);
  expect(drawerOpened.drawerExpanded === "true", tag + ": #burger did not announce the open side tab");
  await page.locator("#drawerClose").focus();
  await page.keyboard.press("Escape");
  await page.waitForFunction(
    () => document.querySelector("#drawer")?.getAttribute("data-open") === "false",
    null, { timeout: 1500 }
  );
  const drawerClosed = await readState(page);
  expect(drawerClosed.drawerExpanded === "false", tag + ": #burger stayed expanded after Escape");
  expect(drawerClosed.active === "#burger", tag + ": drawer Escape returned focus to "
    + drawerClosed.active + ", not #burger");

  await page.locator("#burger").click();
  await page.waitForFunction(
    () => document.querySelector("#drawer")?.getAttribute("data-open") === "true",
    null, { timeout: 1500 }
  );
  await page.locator("#drawerClose").click();
  await page.waitForTimeout(50);
  const drawerButtonClosed = await readState(page);
  expect(drawerButtonClosed.drawerOpen === "false", tag + ": the close button left the side tab open");
  expect(drawerButtonClosed.active === "#burger", tag + ": the close button returned focus to "
    + drawerButtonClosed.active + ", not #burger");

  const mainLink = page.locator("main a").first();
  if (await mainLink.count()) {
    await mainLink.focus();
    const before = await readState(page);
    await page.keyboard.press("Escape");
    const after = await readState(page);
    expect(after.active === before.active, tag + ": idle Escape moved focus from "
      + before.active + " to " + after.active);
  }

  if (errors.length) fail(tag + " console: " + errors.join(" | "));
}

(async () => {
  const browser = await chromium.launch({ args: ["--no-sandbox"] });
  try {
    for (const path of SAMPLES) {
      const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
      const page = await context.newPage();
      await checkPage(page, path);
      await context.close();
    }
  } finally {
    await browser.close();
  }

  if (failures.length) {
    console.error("menu: FAILED");
    for (const problem of failures) console.error("  " + problem);
    process.exit(1);
  }
  console.log("menu: OK - initial ARIA and focus return on " + SAMPLES.length
    + " pages (chevron, page name, side tab and close button)");
})().catch((error) => {
  console.error("menu: FAILED - " + (error.stack || error.message));
  process.exit(1);
});
