#!/usr/bin/env node
/**
 * Export the shelf as Figma-importable SVG artboards.
 *
 *   python3 -m http.server 8321 --directory docs &
 *   node tools/figma_export.js http://127.0.0.1:8321/ figma --preview
 *
 * This is not a redraw. The script opens each page in Chromium at the real
 * viewport, then translates the *measured* result into SVG: every visible box
 * becomes a <rect> that keeps its computed fill and its hairline borders, every
 * text run becomes a <text> on its real baseline, and every inline SVG mark or
 * <img> is rasterised in place. Figma imports <text> as live text layers and
 * the rects as vectors, so the file stays editable while matching the page.
 *
 * Requires the render toolchain (see tools/render_env.py):
 *   PLAYWRIGHT_BROWSERS_PATH, LD_LIBRARY_PATH, FONTCONFIG_FILE, NODE_PATH.
 *
 * --preview also screenshots each exported SVG into artifacts/ so the result can
 * be compared against the page it came from.
 */
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const PAGES = [
  { slug: "index", url: "/", label: "Index" },
  { slug: "icons", url: "/icons/", label: "Icons" },
  { slug: "icons-all", url: "/icons/all/", label: "Icons · All brands" },
  { slug: "icons-lineicons", url: "/icons/lineicons/", label: "Icons · Lineicons" },
  { slug: "icons-coolicons", url: "/icons/coolicons/", label: "Icons · Coolicons" },
  { slug: "fonts", url: "/fonts/", label: "Fonts" },
  { slug: "tools", url: "/tools/", label: "Tools" },
  { slug: "portfolio", url: "/portfolio/", label: "Portfolio" },
  { slug: "download", url: "/download/", label: "Download" },
];

const WIDTHS = [
  { w: 1300, h: 900, tag: "desktop" },
  { w: 390, h: 844, tag: "phone" },
];

/* ------------------------------------------------------------------ */
/* runs inside the page                                                */
/* ------------------------------------------------------------------ */

async function capture() {
  const SKIP = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE", "HEAD", "META", "LINK"]);
  const round = (v) => Math.round(v * 100) / 100;
  const esc = (s) =>
    s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const scrollY = window.scrollY;
  const W = document.documentElement.clientWidth;
  const H = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight);

  const color = (c) => {
    if (!c) return null;
    const m = c.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(/[,\s/]+/).filter(Boolean).map(Number);
    const hex = "#" + p.slice(0, 3).map((v) => v.toString(16).padStart(2, "0")).join("");
    const a = p.length > 3 ? p[3] : 1;
    return a > 0.001 ? { hex, a } : null;
  };
  const paint = (c) => {
    const p = color(c);
    if (!p) return "";
    return `fill="${p.hex}"${p.a < 0.999 ? ` fill-opacity="${round(p.a)}"` : ""}`;
  };

  const ctx = document.createElement("canvas").getContext("2d");
  const metrics = (font, text) => {
    ctx.font = font;
    const m = ctx.measureText(text || "x");
    return {
      asc: m.fontBoundingBoxAscent || m.actualBoundingBoxAscent || 0,
      desc: m.fontBoundingBoxDescent || m.actualBoundingBoxDescent || 0,
      w: m.width,
    };
  };

  const unescapeCss = (s) =>
    s
      .replace(/\\([0-9a-fA-F]{1,6})\s?/g, (_, h) => String.fromCodePoint(parseInt(h, 16)))
      .replace(/\\(.)/g, "$1");

  // CSS-generated text never appears in the DOM: the "→" after a card's link
  // and the "01" numbering on a steps list are painted by the browser alone, so
  // they are read back off the pseudo's computed style instead.
  const pseudoText = (node, which) => {
    const ps = getComputedStyle(node, which);
    const raw = ps.content;
    if (!raw || raw === "none" || raw === "normal") return null;
    let text = "";
    const re = /"((?:[^"\\]|\\.)*)"|counter\(\s*[\w-]+\s*\)/g;
    let m;
    while ((m = re.exec(raw))) {
      if (m[1] !== undefined) {
        text += unescapeCss(m[1]);
        continue;
      }
      // counter(): the only counters on this site are the steps lists, and only
      // an <ol> can be resolved without asking the layout engine.
      const parent = node.parentElement;
      if (!parent || parent.tagName.toLowerCase() !== "ol") return null;
      const idx = Array.from(parent.children).indexOf(node) + 1;
      text = text.replace(/0$/, "") + String(idx).padStart(2, "0");
    }
    return text.trim() ? { text, cs: ps } : null;
  };

  const raster = async (el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return null;
    const scale = Math.min(3, Math.max(1, 900 / r.width));
    const cv = document.createElement("canvas");
    cv.width = Math.round(r.width * scale);
    cv.height = Math.round(r.height * scale);
    const g = cv.getContext("2d");
    if (el.tagName.toLowerCase() === "svg") {
      const clone = el.cloneNode(true);
      clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
      clone.setAttribute("width", r.width);
      clone.setAttribute("height", r.height);
      // currentColor would resolve to black outside the page.
      clone.style.color = getComputedStyle(el).color;
      const src =
        "data:image/svg+xml;charset=utf-8," +
        encodeURIComponent(new XMLSerializer().serializeToString(clone));
      const img = new Image();
      await new Promise((res, rej) => {
        img.onload = res;
        img.onerror = rej;
        img.src = src;
      });
      g.drawImage(img, 0, 0, cv.width, cv.height);
    } else {
      g.drawImage(el, 0, 0, cv.width, cv.height);
    }
    return cv.toDataURL("image/png");
  };

  const out = [];
  let rects = 0, texts = 0, images = 0;

  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
    acceptNode(n) {
      if (n.nodeType === 1) {
        if (SKIP.has(n.tagName)) return NodeFilter.FILTER_REJECT;
        const g = getComputedStyle(n);
        if (g.display === "none" || g.visibility === "hidden") return NodeFilter.FILTER_REJECT;
        if (parseFloat(g.opacity) === 0) return NodeFilter.FILTER_REJECT;
        // An <svg> is visited so it can be rasterised as a whole; its own
        // children are zero-size boxes and emit nothing.
        return NodeFilter.FILTER_ACCEPT;
      }
      if (!n.nodeValue || !n.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
      return NodeFilter.FILTER_ACCEPT;
    },
  });

  const range = document.createRange();
  let node;
  while ((node = walker.nextNode())) {
    if (node.nodeType === 1) {
      const g = getComputedStyle(node);
      // An inline element that wraps has one box per line; a highlight with
      // box-decoration-break:clone paints one bar per line, so every fragment
      // is measured, not the union.
      const frags = Array.from(node.getClientRects()).filter((r) => r.width > 0.5 && r.height > 0.5);
      for (const r of frags) {
        const top = r.top + scrollY;
        if (color(g.backgroundColor)) {
          const rx = parseFloat(g.borderTopLeftRadius) || 0;
          out.push(
            `<rect x="${round(r.left)}" y="${round(top)}" width="${round(r.width)}" height="${round(
              r.height
            )}" ${paint(g.backgroundColor)}${rx ? ` rx="${round(rx)}"` : ""}/>`
          );
          rects++;
        }
        for (const side of ["Top", "Right", "Bottom", "Left"]) {
          const bw = parseFloat(g["border" + side + "Width"]) || 0;
          if (bw <= 0 || g["border" + side + "Style"] === "none") continue;
          if (!color(g["border" + side + "Color"])) continue;
          let x = r.left, y = top, w = r.width, h = r.height;
          if (side === "Top") h = bw;
          else if (side === "Bottom") { y = top + r.height - bw; h = bw; }
          else if (side === "Left") w = bw;
          else { x = r.left + r.width - bw; w = bw; }
          out.push(
            `<rect x="${round(x)}" y="${round(y)}" width="${round(w)}" height="${round(h)}" ${paint(
              g["border" + side + "Color"]
            )}/>`
          );
          rects++;
        }
      }
      // Generated content, placed from the box it attaches to: a "::before"
      // starts at the element's content edge, a "::after" follows its last
      // fragment. Inline boxes already sit on the line box, block ones are
      // centred inside their line-height.
      const tagName = node.tagName.toLowerCase();
      if (tagName !== "svg" && tagName !== "img" && tagName !== "canvas") {
        const box = getComputedStyle(node);
        for (const which of ["::before", "::after"]) {
          const ps = pseudoText(node, which);
          if (!ps) continue;
          const frags = Array.from(node.getClientRects()).filter((r) => r.width > 0.5 && r.height > 0.5);
          if (!frags.length) continue;
          const frag = which === "::before" ? frags[0] : frags[frags.length - 1];
          const font = `${ps.cs.fontStyle} ${ps.cs.fontWeight} ${ps.cs.fontSize} ${ps.cs.fontFamily}`;
          const pm = metrics(font, ps.text);
          const x =
            which === "::before"
              ? frag.left + (parseFloat(box.paddingLeft) || 0)
              : frag.right - (parseFloat(box.paddingRight) || 0);
          const edge = which === "::before" ? frag.top + (parseFloat(box.paddingTop) || 0) : frag.top;
          const lh = parseFloat(ps.cs.lineHeight) || parseFloat(ps.cs.fontSize) * 1.65;
          const baseline = /^inline/.test(box.display)
            ? edge + pm.asc
            : edge + Math.max(0, (lh - (pm.asc + pm.desc)) / 2) + pm.asc;
          out.push(
            `<text x="${round(x)}" y="${round(baseline + scrollY)}" ${paint(
              ps.cs.color
            )} font-family="Poppins" font-size="${round(parseFloat(ps.cs.fontSize))}" font-weight="${
              ps.cs.fontWeight
            }" xml:space="preserve">${esc(ps.text)}</text>`
          );
          texts++;
        }
      }

      const r = node.getBoundingClientRect();
      const top = r.top + scrollY;

      const tag = node.tagName.toLowerCase();
      if (tag === "svg" || tag === "img" || tag === "canvas") {
        try {
          const uri = await raster(node);
          if (uri) {
            out.push(
              `<image x="${round(r.left)}" y="${round(top)}" width="${round(r.width)}" height="${round(
                r.height
              )}" href="${uri}" xlink:href="${uri}"/>`
            );
            images++;
          }
        } catch (e) {
          /* a mark that will not rasterise is left out, not faked */
        }
      }
      continue;
    }

    // text node
    const el = node.parentElement;
    if (!el) continue;
    const g = getComputedStyle(el);
    let value = node.nodeValue;
    if (g.textTransform === "uppercase") value = value.toUpperCase();
    else if (g.textTransform === "lowercase") value = value.toLowerCase();
    else if (g.textTransform === "capitalize") value = value.replace(/(^|\s)(\p{L})/gu, (m, s, c) => s + c.toUpperCase());

    range.selectNodeContents(node);
    const whole = Array.from(range.getClientRects()).filter((r) => r.width > 0 || r.height > 0);
    if (!whole.length) continue;

    const lines = [];
    if (whole.length === 1) {
      lines.push({
        text: value.trim(),
        left: whole[0].left,
        top: whole[0].top,
        bottom: whole[0].bottom,
      });
    } else {
      const boxes = [];
      for (let i = 0; i < value.length; i++) {
        if (/\s/.test(value[i])) {
          boxes.push(null);
          continue;
        }
        range.setStart(node, i);
        range.setEnd(node, i + 1);
        const rr = range.getClientRects()[0];
        boxes.push(rr ? { top: rr.top, bottom: rr.bottom, left: rr.left, right: rr.right } : null);
      }
      let cur = null;
      for (let i = 0; i < boxes.length; i++) {
        const b = boxes[i];
        if (!b) {
          if (cur) cur.end = i;
          continue;
        }
        if (cur && Math.abs(b.top - cur.top) <= 1.5) {
          cur.end = i + 1;
          cur.left = Math.min(cur.left, b.left);
          cur.right = Math.max(cur.right, b.right);
          cur.bottom = Math.max(cur.bottom, b.bottom);
        } else {
          if (cur) lines.push(cur);
          cur = { text: "", left: b.left, right: b.right, top: b.top, bottom: b.bottom, start: i, end: i + 1 };
        }
      }
      if (cur) lines.push(cur);
      for (const l of lines) l.text = value.slice(l.start, l.end).trim();
    }

    const font = `${g.fontStyle} ${g.fontWeight} ${g.fontSize} ${g.fontFamily}`;

    // The browser cuts long labels with an ellipsis inside a fixed box; cut
    // them to the same width so a glyph's name cannot run over its neighbour.
    let clipWidth = null;
    for (let e = el; e && e !== document.body; e = e.parentElement) {
      const s = getComputedStyle(e);
      if (s.textOverflow === "ellipsis" && s.overflow !== "visible") {
        const pad = (parseFloat(s.paddingLeft) || 0) + (parseFloat(s.paddingRight) || 0);
        clipWidth = Math.max(6, (e.clientWidth || e.getBoundingClientRect().width) - pad);
        break;
      }
    }

    for (const l of lines) {
      if (!l.text) continue;
      let lineText = l.text;
      if (clipWidth && metrics(font, lineText).w > clipWidth) {
        let lo = 0;
        let hi = lineText.length;
        while (lo < hi) {
          const mid = Math.ceil((lo + hi) / 2);
          if (metrics(font, lineText.slice(0, mid) + "…").w <= clipWidth) lo = mid;
          else hi = mid - 1;
        }
        lineText = lineText.slice(0, lo).replace(/[\s,.;:·—–-]+$/, "") + "…";
      }
      const m = metrics(font, lineText);
      const baseline = l.top + scrollY + m.asc;
      const ls = g.letterSpacing && g.letterSpacing !== "normal" ? ` letter-spacing="${round(parseFloat(g.letterSpacing))}"` : "";
      out.push(
        `<text x="${round(l.left)}" y="${round(baseline)}" ${paint(g.color)} font-family="Poppins" font-size="${round(
          parseFloat(g.fontSize)
        )}" font-weight="${g.fontWeight}"${g.fontStyle === "italic" ? ' font-style="italic"' : ""}${
          g.textDecorationLine.includes("underline") ? ' text-decoration="underline"' : ""
        }${ls} xml:space="preserve">${esc(lineText)}</text>`
      );
      texts++;
    }
  }

  const bg = getComputedStyle(document.body).backgroundColor;
  return { W, H, bg, body: out.join("\n"), counts: { rects, texts, images } };
}

/* ------------------------------------------------------------------ */

(async () => {
  const base = (process.argv[2] || "http://127.0.0.1:8321/").replace(/\/?$/, "/");
  const outDir = path.resolve(process.argv[3] || "figma");
  const previewArg = process.argv.find((a) => a === "--preview" || a.startsWith("--preview="));
  const preview = previewArg ? (previewArg.includes("=") ? previewArg.split("=")[1] : true) : false;
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch();
  const errors = [];
  const written = [];

  const pagesArg = process.argv.find((a) => a.startsWith("--pages="));
  const onlyPages = pagesArg ? pagesArg.split("=")[1].split(",") : null;
  const widthsArg = process.argv.find((a) => a.startsWith("--widths="));
  const onlyWidths = widthsArg ? widthsArg.split("=")[1].split(",") : null;

  for (const width of WIDTHS) {
    if (onlyWidths && !onlyWidths.includes(width.tag)) continue;
    for (const page_ of PAGES) {
      if (onlyPages && !onlyPages.includes(page_.slug)) continue;
      const ctx = await browser.newContext({
        viewport: { width: width.w, height: width.h },
        deviceScaleFactor: 1,
        reducedMotion: "reduce",
      });
      const page = await ctx.newPage();
      page.on("console", (m) => {
        if (m.type() === "error") errors.push(`${page_.slug}/${width.tag}: ${m.text()}`);
      });
      page.on("pageerror", (e) => errors.push(`${page_.slug}/${width.tag}: ${e.message}`));
      await page.goto(base + page_.url.replace(/^\//, ""), { waitUntil: "networkidle" });
      await page.evaluate(() => document.fonts.ready);
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      await page.waitForTimeout(150);
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.waitForTimeout(150);

      const shot = await page.evaluate(capture);
      // Number by position in the full run, so --pages/--widths filters still
      // write each artboard to its own stable file name.
      const n = String(WIDTHS.indexOf(width) * PAGES.length + PAGES.indexOf(page_) + 1).padStart(2, "0");
      const file = `${n}-${page_.slug}-${width.tag}.svg`;
      const head =
        `<?xml version="1.0" encoding="UTF-8"?>\n` +
        `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" ` +
        `width="${shot.W}" height="${shot.H}" viewBox="0 0 ${shot.W} ${shot.H}">\n` +
        `<title>${page_.label} — ${width.w}×${shot.H}</title>\n` +
        `<g id="${page_.label.replace(/[^\w· -]/g, "")} · ${width.tag}">\n` +
        `<rect x="0" y="0" width="${shot.W}" height="${shot.H}" fill="${(shot.bg.match(/\d+/g) || [63, 78, 150])
          .slice(0, 3)
          .map((v) => Number(v).toString(16).padStart(2, "0"))
          .reduce((a, v) => a + v, "#")}"/>\n`;
      const body = head + shot.body + "\n</g>\n</svg>\n";
      fs.writeFileSync(path.join(outDir, file), body);
      written.push({
        file,
        label: page_.label,
        tag: width.tag,
        size: `${shot.W}×${shot.H}`,
        bytes: Buffer.byteLength(body),
        ...shot.counts,
      });
      await ctx.close();
    }
  }

  await browser.close();

  for (const w of written) {
    console.log(
      `${w.file.padEnd(34)} ${w.size.padEnd(10)} ${String(Math.round(w.bytes / 1024)).padStart(5)} KB  ` +
        `${w.rects} rects · ${w.texts} text · ${w.images} images`
    );
  }
  if (errors.length) {
    console.log(`\nconsole errors (${errors.length}):`);
    for (const e of errors) console.log("  " + e);
  } else {
    console.log("\nno console errors");
  }

  if (preview) {
    const dir = path.resolve("artifacts");
    fs.mkdirSync(dir, { recursive: true });
    // A whole 9000px artboard with dozens of inlined images can outrun the
    // screenshot budget, so previews are clipped to the top of each frame.
    const only = preview === true ? null : String(preview).split(",");
    const b2 = await chromium.launch();
    for (const w of written) {
      if (only && !only.some((p) => w.file.startsWith(p))) continue;
      const [width, tall] = w.size.split("×").map(Number);
      const height = Math.min(1100, tall);
      const ctx = await b2.newContext({ viewport: { width, height }, deviceScaleFactor: 1 });
      const page = await ctx.newPage();
      await page.goto("file://" + path.join(outDir, w.file), { waitUntil: "load" });
      await page.waitForTimeout(300);
      try {
        await page.screenshot({
          path: path.join(dir, `figma-${w.file.replace(/\.svg$/, "")}.png`),
          clip: { x: 0, y: 0, width, height },
          timeout: 120000,
          animations: "disabled",
        });
      } catch (e) {
        console.log(`preview failed for ${w.file}: ${String(e.message).split("\n")[0]}`);
      }
      await ctx.close();
    }
    await b2.close();
    console.log(`previews -> artifacts/figma-*.png`);
  }

  process.exit(errors.length ? 1 : 0);
})();
