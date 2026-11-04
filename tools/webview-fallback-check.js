// Drive the Icons app's unbundled-page fallback without a device.
//
// The fallback is not a file in the staged bundle: MainActivity answers a page the
// app does not carry with HTML it builds in Java, so serving assets/www can never
// reach it. This reads the finished strings out of the shipped classes.dex, rebuilds
// the document around one address exactly as the client does, and loads that page in
// headless Chromium -- the shipped bytes, not a copy of the source.
//
//   node tools/webview-fallback-check.js [apk] [screenshot]
//   node tools/webview-fallback-check.js --static [apk]
//
// Playwright is loaded only where the page is actually rendered, so --static still
// works on a machine without the toolchain -- which is the whole point of the fallback
// build.sh takes when the module is absent.
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const args = process.argv.slice(2);
const staticOnly = args.includes("--static");
const positional = args.filter((a) => a !== "--static");
const apk = positional[0] || path.join(root, "docs", "Icons-2.20.apk");
const shot = positional[1] || path.join(root, "artifacts", "fallback-offline.png");

const dex = execFileSync("unzip", ["-p", apk, "classes.dex"], { maxBuffer: 96 * 1024 * 1024 });
// Dex strings are NUL-terminated runs of UTF-8. latin1 keeps the bytes one-to-one,
// and decoding restores the middot in "Icons · offline" so a fold is not cut there.
const strings = (dex.toString("latin1").match(/[\x20-\x7e\x80-\xff]{8,}/g) || [])
  .map((s) => Buffer.from(s, "latin1").toString("utf8"));

const prefix = strings.find((s) => s.startsWith("<!doctype html>") && s.includes("<code style="));
const middle = strings.find((s) => s.startsWith("</code></p><a href=\""));
const suffix = strings.find((s) => s.startsWith("\" style=\"display:inline-block") && s.includes("Back to the icons"));
if (!prefix || !middle || !suffix) {
  console.error("fallback fragments not found in", apk);
  console.error({ doctype: strings.filter((s) => s.startsWith("<!doctype")), code: strings.filter((s) => s.startsWith("</code>")) });
  process.exit(1);
}

const address = "https://artbase-olive.vercel.app/download/";
const page = prefix + address + middle + address + suffix;
const checks = {
  saysEveryBrand: page.includes("every brand, their catalogs"),
  noBothBrands: !page.includes("both brands"),
  heading: (page.match(/<h1[^>]*>([^<]+)<\/h1>/) || [])[1] || null,
  addresses: page.split(address).length - 1,
  bytes: page.length,
};

const staticOk = checks.saysEveryBrand && checks.noBothBrands
  && checks.heading === "That page lives on the site" && checks.addresses === 2
  && page.includes(">Open on the web</a>") && page.includes('href="/icons/index.html"');

if (staticOnly) {
  // Builds without the render toolchain still refuse a stale fallback; they just
  // cannot prove the layout.
  console.log(JSON.stringify(checks, null, 2));
  console.log(staticOk ? "fallback: OK (static check, not rendered)" : "fallback: FAILED");
  process.exit(staticOk ? 0 : 1);
}

(async () => {
  const { chromium } = require("playwright");
  const browser = await chromium.launch({ args: ["--no-sandbox"] });
  const tab = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const errors = [];
  tab.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  tab.on("pageerror", (e) => errors.push("pageerror " + e.message));
  await tab.setContent(page, { waitUntil: "load" });
  checks.rendered = await tab.evaluate(() => {
    const links = Array.from(document.querySelectorAll("a")).map((a) => ({ text: a.textContent.trim(), href: a.getAttribute("href") }));
    const body = document.body.innerText.replace(/\s+/g, " ").trim();
    return {
      title: document.querySelector("h1")?.textContent.trim() || null,
      body,
      links,
      background: getComputedStyle(document.body).backgroundColor,
      font: getComputedStyle(document.body).fontFamily.split(",")[0],
      overflowX: document.documentElement.scrollWidth > window.innerWidth,
    };
  });
  checks.consoleErrors = errors;
  fs.mkdirSync(path.dirname(shot), { recursive: true });
  await tab.screenshot({ path: shot, fullPage: true });
  await browser.close();
  checks.screenshot = path.relative(root, shot);
  const ok = staticOk && checks.rendered.title === "That page lives on the site"
    && checks.rendered.links.length === 2 && checks.addresses === 2 && errors.length === 0 && !checks.rendered.overflowX;
  console.log(JSON.stringify(checks, null, 2));
  console.log(ok ? "fallback: OK" : "fallback: FAILED");
  process.exit(ok ? 0 : 1);
})();
