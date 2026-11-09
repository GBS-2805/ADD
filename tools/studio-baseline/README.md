# Studio WebView visual baselines

These PNGs are the pixel baselines for `tools/studio-interaction-check.js`. The
check drives the tracked Studio source in headless Chromium at 390×844, with a
touch-capable mobile viewport, and compares two deterministic states:

- `studio-snapped-guides.png` — a selected layer after edge snapping, with the
  selection frame and both alignment guides visible.
- `studio-path-open.png` — the same layer with the editable MotionPath curve and
  its four handles visible.

The comparison allows at most 40 changed pixels (`STUDIO_VISUAL_MAX_DIFF`) at a
per-channel threshold of 8, so sub-pixel antialiasing noise does not fail the
guard while layout, colour, guide and handle regressions do.

Refresh the baselines only after reviewing the rendered change:

```bash
NODE_PATH=/tmp/pwshot/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw \
LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu \
FONTCONFIG_FILE=/tmp/rootfs/fonts.conf \
STUDIO_VISUAL_UPDATE=1 node tools/studio-interaction-check.js
```

Then run without `STUDIO_VISUAL_UPDATE` to verify against the committed files.
A failure writes `artifacts/<name>.diff.png` beside the actual capture.

`python3 tools/check_all.py --visual` runs this with the rest of the verdict, and
`.github/workflows/checks.yml` runs it on every push. The CI job installs Chromium
at the pinned Playwright version, because the browser build is part of what decides
the pixels: a bump means re-recording, not a quiet pass.

The guard uses Chromium, not the Android WebView. Insets, clipboard bridge, and
device touch behaviour still require a phone check.
