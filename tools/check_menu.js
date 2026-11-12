// The shared band has two disclosures: the chevron (#menuBtn) and the ADD
// wordmark (.site-wordmark). The old .page-name is intentionally removed by
// site.js, so it is not a control. Both real disclosures must start closed and
// closing either panel must return focus to whichever one opened it.
//
//   node tools/check_menu.js http://127.0.0.1:8799/
//
// This is the browser half of check_all.py --browser. It drives the home page,
// a nested shelf page and a resource page at phone and desktop widths, because
// the same chrome is shared but the markup is generated in different places.
const { chromium } = require("playwright");

const BASE = process.argv[2];
if (!BASE || !BASE.endsWith("/")) {
  console.error("usage: node tools/check_menu.js http://127.0.0.1:8799/  (ends in a slash)");
  process.exit(2);
}

const SAMPLES = [
  { name: "home", path: "" },
  { name: "nested shelf page", path: "icons/coolicons/" },
  { name: "resource page", path: "releases/" }
];
const VIEWPORTS = [
  { name: "phone", width: 390, height: 844 },
  { name: "desktop", width: 1300, height: 900 }
];
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
    const trigger = get(".bar .site-wordmark");
    const chevron = get("#menuBtn");
    const burger = get("#burger");
    const visible = (node) => {
      if (!node) return false;
      const box = node.getBoundingClientRect();
      const style = getComputedStyle(node);
      return box.width > 0 && box.height > 0
        && style.display !== "none" && style.visibility !== "hidden";
    };
    const describe = (node) => {
      if (!node) return "none";
      if (node.id) return "#" + node.id;
      if (node.classList && node.classList.contains("site-wordmark")) return ".site-wordmark";
      if (node.classList && node.classList.contains("burger")) return ".burger";
      return node.tagName.toLowerCase();
    };
    return {
      active: describe(active),
      triggerRole: trigger ? trigger.getAttribute("role") : "missing",
      triggerExpanded: trigger ? trigger.getAttribute("aria-expanded") : "missing",
      triggerControls: trigger ? trigger.getAttribute("aria-controls") : "missing",
      triggerLabel: trigger ? trigger.getAttribute("aria-label") : "missing",
      triggerVisible: visible(trigger),
      chevronVisible: visible(chevron),
      chevronInMenu: !!(chevron && chevron.closest("#menu")),
      burgerVisible: visible(burger),
      menuExpanded: chevron ? chevron.getAttribute("aria-expanded") : "missing",
      menuOpen: get("#menu")?.getAttribute("data-open") ?? "missing",
      drawerExpanded: burger ? burger.getAttribute("aria-expanded") : "missing",
      drawerOpen: get("#drawer")?.getAttribute("data-open") ?? "missing"
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
  expect(opened.triggerExpanded === "true", label + ": wordmark did not announce the open menu");
  expect(opened.menuExpanded === "true", label + ": #menuBtn did not announce the open menu");
  expect(opened.menuOpen === "true", label + ": the sections menu did not open");

  await page.locator("#menu a").first().focus();
  const inside = await page.evaluate(() => !!document.activeElement.closest("#menu"));
  expect(inside, label + ": a menu link could not take focus");

  await page.keyboard.press("Escape");
  await page.waitForFunction(
    () => document.querySelector("#menu")?.getAttribute("data-open") === "false",
    null, { timeout: 1500 }
  );
  const closed = await readState(page);
  expect(closed.triggerExpanded === "false", label + ": wordmark stayed expanded after Escape");
  expect(closed.menuExpanded === "false", label + ": #menuBtn stayed expanded after Escape");
  expect(closed.menuOpen === "false", label + ": the sections menu stayed open after Escape");
  expect(closed.active === expected, label + ": Escape returned focus to " + closed.active
    + ", not " + expected);
}

async function checkPage(page, sample, viewport) {
  const tag = sample.name + " " + viewport.name;
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push("pageerror " + error.message));

  await page.goto(BASE + sample.path, { waitUntil: "load" });
  await page.waitForFunction(() => {
    const trigger = document.querySelector(".bar .site-wordmark");
    return !!trigger && trigger.getAttribute("role") === "button";
  }, null, { timeout: 15000 });

  const initial = await readState(page);
  expect(initial.triggerRole === "button", tag + ": wordmark is not a button");
  expect(initial.triggerExpanded === "false", tag + ": wordmark did not start with aria-expanded=false");
  expect(initial.triggerControls === "menu", tag + ": wordmark does not control the sections menu");
  expect(/sections/i.test(initial.triggerLabel), tag + ": wordmark is not labelled as the sections trigger");
  expect(initial.triggerVisible, tag + ": the wordmark trigger is not visible");
  expect(initial.chevronVisible, tag + ": the chevron trigger is not visible");
  expect(!initial.chevronInMenu, tag + ": the chevron opener is trapped inside the closed menu");
  expect(initial.burgerVisible, tag + ": the side-tab trigger is not visible");
  expect(initial.menuOpen === "false", tag + ": the sections menu started open");
  expect(initial.menuExpanded === "false", tag + ": #menuBtn started expanded");
  expect(initial.drawerOpen === "false", tag + ": the side tab started open");
  expect(initial.drawerExpanded === "false", tag + ": #burger started expanded");

  await openMenuWith(page, "#menuBtn", "#menuBtn", tag + " chevron");
  await openMenuWith(page, ".site-wordmark", ".site-wordmark", tag + " wordmark");

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
    for (const viewport of VIEWPORTS) {
      for (const sample of SAMPLES) {
        const context = await browser.newContext({
          viewport: { width: viewport.width, height: viewport.height }
        });
        const page = await context.newPage();
        await checkPage(page, sample, viewport);
        await context.close();
      }
    }
  } finally {
    await browser.close();
  }

  if (failures.length) {
    console.error("menu: FAILED");
    for (const problem of failures) console.error("  " + problem);
    process.exit(1);
  }
  console.log("menu: OK - " + SAMPLES.length + " pages at " + VIEWPORTS.length
    + " widths; chevron and wordmark open, Escape closes and returns focus, side tab and close button pass");
})().catch((error) => {
  console.error("menu: FAILED - " + (error.stack || error.message));
  process.exit(1);
});
