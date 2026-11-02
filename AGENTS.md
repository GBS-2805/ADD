# Working rules for this repo

## Renders: only when the change is visual

A change to a page in `docs/` needs image renders **only if it changes what the page
looks like**: layout, spacing, colour, typography, iconography, or the presence and
position of an element.

**Skip renders entirely** -- and skip `docs/renders/`, the gallery page and the
deploy that would carry them -- for changes that cannot alter appearance:

- copy and wording edits, including headings, labels and alt text;
- link and URL changes, or swapping a `href`, `src` target or favicon path;
- attributes, metadata, comments, formatting and other non-visual markup;
- anything outside `docs/` at all.

When skipping, say so in one line in the reply (`No render: copy-only change`) so the
omission reads as a decision rather than an oversight. Do not capture a page to prove
that text changed.

**When a render is warranted**:

- Capture with headless Chromium via Playwright, served from a local static server
  over `docs/`, at 1300x900. `tools/render_pages.js` does the whole set -- home page,
  its full height, the open menu and the phone widths -- in about 4 seconds and
  prints console errors.
- Save under `artifacts/`, one file per page the change touches
  (`page-home.png`, `page-home-full.png`, `phone-home.png`, `page-icons.png`, ...).
  There is one palette, so there are no dark variants; gallery files already on the
  site keep their existing `-light` names.
- Cover every page the change touches, plus mobile width when the change is
  responsive.
- Inspect each render before sending it. If the render shows a layout, colour or
  overflow problem, fix the page and re-render; never ship a screenshot of a bug.
- Report console errors seen while capturing. A render that only proves HTTP 200
  is not a render.
- Standalone artwork (SVG marks, icons) is additionally rendered with
  `@resvg/resvg-js` so the asset itself is verifiable apart from the page.
- Only then copy into `docs/renders/`, add it to `docs/renders/index.html`, and
  deploy -- the gallery only shows files listed there.

### Renders are captures, never drawings

Every image in `docs/renders/` is a screenshot of a page that actually ran. Do not
draw the Android app from its `dp`/`sp` values and do not recreate a screen for the
gallery: a reconstruction reads as a screenshot without being one, and this was
already shipped once and removed.

The app cannot be captured on this VM (see below), so the gallery carries no app
images. When an image stops being a capture, delete the file from `docs/renders/`
and its entry from the gallery in the same change; leave no orphan PNGs behind.

### How renders are sent (learned the hard way)

The person reading this works from a phone. Deliver renders like this, every time:

1. Lead with **one tappable markdown link to the live site**:
   `[Open the site](https://<site-host>/)`.
2. Follow immediately with **one tappable markdown link to the gallery**:
   `[Open the renders gallery](https://<site-host>/renders/)`.
3. Offer individual images as **short labelled markdown links**
   (`[Compress page — 1300×900](https://<site-host>/renders/x.png)`).
4. **Never** paste a bare `https://...` URL as the only way in: long URLs wrap
   mid-address and a phone will not linkify them.
5. **Never** rely on workspace-relative paths (`artifacts/x.png`) or inline
   `![](...)` embeds built from them — neither resolves outside the desktop app.
6. Verify before sending: the gallery returns 200, every listed image loads with a
   real `naturalWidth`, and no capture is missing from the page.

## Local render setup (no root, no system Chrome)

This VM has bare Chromium deps and zero fonts. `python3 tools/render_env.py` builds
the whole toolchain in one go (~4 min, ~300 MB) and prints the exports below; the
recipe underneath is what it automates, in case it breaks.

    npm i playwright && npx playwright install chromium   # PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    # unpack the missing runtime libs rootlessly from the Ubuntu archive:
    #   parse dists/noble/{main,universe}/binary-amd64/Packages.gz for the
    #   Filenames of libnspr4 libnss3 libasound2t64 libatk1.0-0t64
    #   libatk-bridge2.0-0t64 libatspi2.0-0t64 libxkbcommon0 libxcomposite1
    #   libxdamage1 libxfixes3 libxrandr2 libxrender1 libxcb-render0 libxcb-shm0
    #   libpixman-1-0 libharfbuzz0b libgraphite2-3 libfreetype6 libfontconfig1
    #   libpng16-16t64 libbrotli1 libx11-xcb1 libwayland-* fonts-dejavu-core
    #   fonts-liberation libxi6 libxtst6 libegl1 libglx0, then `dpkg -x` them
    #   into /tmp/rootfs. apt-get download fails here (stale indexes).
    export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf   # <dir>/tmp/rootfs/usr/share/fonts</dir>

Screenshots are of a local server over `docs/` (`python3 -m http.server`), viewport
1300x900, and 390x844 for phone shots. `node tools/render_pages.js <baseUrl>
<outDir>` takes the home page, its full height, the open menu and both phone widths
in one run and prints console errors; it needs the toolchain from
`tools/render_env.py`.

The site has one palette -- an indigo field with a light band. `docs/index.html` has
no theme switch any more. `docs/icons/index.html` still carries its own `DARK OFF/ON`
button and night palette, because it has not been restyled yet: capture it in light
only, and delete the button when you regenerate it on the new palette.

### The app cannot be captured here

Do not spend another session on the emulator. `/dev/kvm` exists but this account is
not in the `kvm` group and `sudo` fails with the "no new privileges" flag, so the
emulator runs under TCG. That path does boot (about 195 s), installs and starts the
app, but `system_server` stays in a permanent "Application Not Responding" state
and every screencap is a flat black frame, at any `wm size`/`density`. Report app
changes as unverified on device rather than illustrating them.

## Project shape

- `docs/` is the deployed static site (Sites manifest `manifest.json`, assets from `docs`).
- `docs/renders/` is the gallery page plus the captures it lists. The page shows
  only files named in `index.html`, so the folder must hold exactly those.
- `artifacts/` is scratch: captures in progress, Figma probes, old shots. Nothing
  there is deployed, and stale files in it are not evidence of anything.
- `docs/icons/index.html` is generated by `tools/fetch_icons.py` from
  `tools/icons_template.html`; edit the template and regenerate, never the output.
  It is still on the old cream-and-amber palette, the one page that does not match
  the rest; the gallery says so.
- APK builds go through `build.sh` into `dist/`; released APKs are committed inside
  `docs/`. `dist/` keeps older builds as history, `docs/` ships only the current one.
- The app's palette has one source of truth: `app/res/values/colors.xml`. Names are
  kept from the old theme on purpose, so a rename touches every drawable, layout and
  Java file that references them.
