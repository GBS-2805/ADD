#!/usr/bin/env node
/*
 * Headless Studio interaction proof at phone width.
 *
 * Serves the tracked app/studio-assets directly (not the ignored staged copy),
 * then drives real pointer input through Chromium:
 *   drag -> snapped geometry + visible guides + snapped final state
 *   rotation -> angle change without drifting the layer centre
 *   curve -> editable MotionPath handles and playback to the path end
 *   Observer -> stage pan and wheel zoom, without stealing layer drags
 *   animation -> drag while a path tween is running and a second clean drag
 * Zero console errors are required.
 *
 * Run with:
 *   NODE_PATH=/tmp/pwshot/node_modules PLAYWRIGHT_BROWSERS_PATH=/tmp/pw \
 *   LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu \
 *   FONTCONFIG_FILE=/tmp/rootfs/fonts.conf node tools/studio-interaction-check.js
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const { chromium } = require('playwright');
const { comparePng } = require('./png-compare');

const ROOT = path.resolve(__dirname, '..');
const ASSET_ROOT = path.join(ROOT, 'app', 'studio-assets');
const ARTIFACTS = path.join(ROOT, 'artifacts');
const VISUAL_BASELINE = path.join(ROOT, 'tools', 'studio-baseline');
const VISUAL_MAX_DIFF = Number(process.env.STUDIO_VISUAL_MAX_DIFF || 40);
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
};

function close(actual, expected, tolerance = 0.75) {
  return Math.abs(actual - expected) <= tolerance;
}

function assertClose(actual, expected, label, tolerance = 0.75) {
  assert.ok(close(actual, expected, tolerance), `${label}: expected ${expected}, got ${actual}`);
}

async function visualGuard(page, name) {
  const actualPath = path.join(ARTIFACTS, `${name}.png`);
  const baselinePath = path.join(VISUAL_BASELINE, `${name}.png`);
  await page.screenshot({ path: actualPath, animations: 'disabled', caret: 'hide' });

  if (process.env.STUDIO_VISUAL_UPDATE === '1') {
    fs.mkdirSync(VISUAL_BASELINE, { recursive: true });
    fs.copyFileSync(actualPath, baselinePath);
    console.log(`visual baseline updated: ${name}`);
    return { updated: true, actual: actualPath, baseline: baselinePath };
  }
  if (!fs.existsSync(baselinePath)) {
    throw new Error(`missing Studio baseline ${baselinePath}; run with STUDIO_VISUAL_UPDATE=1`);
  }
  const result = comparePng(actualPath, baselinePath, { threshold: 8 });
  if (result.diffPixels > VISUAL_MAX_DIFF) {
    if (result.diff) {
      fs.writeFileSync(path.join(ARTIFACTS, `${name}.diff.png`), result.diff);
    }
    throw new Error(
      `Studio visual regression in ${name}: ${result.diffPixels}/${result.totalPixels} pixels differ ` +
      `(${(result.ratio * 100).toFixed(3)}%, max channel delta ${result.maxChannelDelta}); ` +
      `see artifacts/${name}.diff.png`
    );
  }
  console.log(`visual guard: ${name} ${result.diffPixels}/${result.totalPixels} px differ (allowed ${VISUAL_MAX_DIFF})`);
  return result;
}

function startServer() {
  const server = http.createServer((req, res) => {
    let pathname = decodeURIComponent(req.url.split('?')[0]);
    if (pathname === '/') { pathname = '/index.html'; }
    const file = path.normalize(path.join(ASSET_ROOT, pathname));
    if (!file.startsWith(ASSET_ROOT + path.sep)) {
      res.writeHead(403); res.end('forbidden'); return;
    }
    fs.readFile(file, (error, data) => {
      if (error) { res.writeHead(404); res.end('not found'); return; }
      res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
      res.end(data);
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

async function main() {
  fs.mkdirSync(ARTIFACTS, { recursive: true });
  const server = await startServer();
  const port = server.address().port;
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    hasTouch: true,
    isMobile: true
  });

  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') { errors.push('console: ' + message.text()); }
  });
  page.on('pageerror', (error) => { errors.push('pageerror: ' + error.message); });

  const state = () => page.evaluate(() => window.__studioInteraction.state());
  const rect = (selector) => page.evaluate((value) => {
    const node = document.querySelector(value);
    return node ? node.getBoundingClientRect().toJSON() : null;
  }, selector);
  const openTool = async (target, name) => {
    await target.click(`[data-tool="${name}"]`);
    await target.waitForSelector(`[data-sheet="${name}"]`, { state: 'visible' });
    await target.waitForTimeout(80);
  };
  const closeInspector = async (target) => {
    await target.click('[data-tool="move"]');
    await target.waitForFunction(() => document.getElementById('peek').hidden);
    await target.waitForTimeout(80);
  };

  try {
    await page.goto(`http://127.0.0.1:${port}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForFunction(() => window.__studioInteraction && window.__studioMotion);
    await page.waitForTimeout(250);

    const initial = await state();
    assert.deepStrictEqual(initial.motion && initial.motion.plays, 0);
    assert.strictEqual(initial.draggableCount, 1);
    assert.strictEqual(initial.rotateDraggableCount, 1);
    assert.strictEqual(initial.pathDraggableCount, 4);
    const plugins = await page.evaluate(() => window.__studioMotion.plugins);
    assert.deepStrictEqual(plugins, ['core', 'Draggable', 'Observer', 'MotionPathPlugin']);

    // A fresh text layer is selected and has a real frame over the canvas.
    await openTool(page, 'type');
    await page.click('#add');
    await page.tap('#text');
    await page.waitForTimeout(120);
    const field = await page.evaluate(() => ({
      active: document.activeElement && document.activeElement.id,
      hidden: document.getElementById('peek').hidden
    }));
    assert.strictEqual(field.active, 'text', 'content field must keep focus');
    assert.strictEqual(field.hidden, false, 'inspector must stay open while editing');
    await page.fill('#text', 'MOVE');
    await page.waitForFunction(() => window.__studioInteraction.state().selected === 'MOVE');
    await closeInspector(page);
    await page.waitForTimeout(250);
    const before = await state();
    assert.strictEqual(before.selected, 'MOVE');
    assert.strictEqual(before.angle, 0);
    const frame = await rect('#selection-frame');
    assert.ok(frame && frame.width > 20 && frame.height > 10, 'selection frame must be visible');

    // Snap the layer's left and top edges to the DESIGN layer's edges. Raw
    // pointer motion deliberately overshoots each target so the guide is a real
    // snapped result and not merely the place the pointer happened to stop.
    const geometry = await page.evaluate(() => {
      const canvas = window.StudioEngine.canvas.wrapperEl.getBoundingClientRect();
      const design = window.StudioEngine.canvas.getObjects().find((object) => object.text === 'DESIGN');
      const box = design.getBoundingRect();
      return {
        canvas: { x: canvas.x, y: canvas.y, width: canvas.width, height: canvas.height },
        scale: canvas.width / window.StudioEngine.W,
        targetX: box.left,
        targetY: box.top
      };
    });
    const rawX = geometry.targetX - before.bbox.left - 5;
    const rawY = geometry.targetY - before.bbox.top + 4;
    const startX = frame.x + frame.width / 2;
    const startY = frame.y + frame.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.waitForTimeout(80);
    await page.mouse.move(startX + rawX * geometry.scale, startY + rawY * geometry.scale, { steps: 18 });
    await page.waitForTimeout(180);

    const during = await page.evaluate(() => {
      const guides = document.getElementById('alignment-guides');
      const vertical = guides.querySelector('.guide-v');
      const horizontal = guides.querySelector('.guide-h');
      return {
        state: window.__studioInteraction.state(),
        guideClass: guides.className,
        guideOpacity: getComputedStyle(guides).opacity,
        vertical: vertical.getBoundingClientRect().toJSON(),
        horizontal: horizontal.getBoundingClientRect().toJSON(),
        verticalDisplay: getComputedStyle(vertical).display,
        horizontalDisplay: getComputedStyle(horizontal).display,
        frame: document.getElementById('selection-frame').getBoundingClientRect().toJSON()
      };
    });

    assert.strictEqual(during.state.guides.active, true, 'guides must be active while snapped');
    assertClose(during.state.bbox.left, geometry.targetX, 'snapped left edge');
    assertClose(during.state.bbox.top, geometry.targetY, 'snapped top edge');
    assertClose(during.state.guides.x, geometry.targetX, 'vertical guide target');
    assertClose(during.state.guides.y, geometry.targetY, 'horizontal guide target');
    assert.strictEqual(during.verticalDisplay, 'block', 'vertical guide must be displayed');
    assert.strictEqual(during.horizontalDisplay, 'block', 'horizontal guide must be displayed');
    assert.strictEqual(during.guideClass, 'active');
    assertClose(during.vertical.x, geometry.canvas.x + geometry.targetX * geometry.scale, 'vertical guide x', 1);
    assertClose(during.horizontal.y, geometry.canvas.y + geometry.targetY * geometry.scale, 'horizontal guide y', 1);

    // The evidence and pixel guard are taken while the pointer is still down.
    const snapVisual = await visualGuard(page, 'studio-snapped-guides');

    await page.mouse.up();
    await page.waitForTimeout(300);
    const afterDrag = await state();
    assert.strictEqual(afterDrag.guides.active, false, 'guides must clear on release');
    assertClose(afterDrag.bbox.left, geometry.targetX, 'final snapped left edge');
    assertClose(afterDrag.bbox.top, geometry.targetY, 'final snapped top edge');
    assert.strictEqual(afterDrag.view.x, 0, 'layer drag must not pan the stage');
    assert.strictEqual(afterDrag.view.y, 0, 'layer drag must not pan the stage');

    // Rotate from the visible amber handle. The visual centre must stay put.
    const handle = await rect('#rotate-handle');
    assert.ok(handle && handle.width > 8, 'rotation handle must be visible');
    const rotateCenter = afterDrag.center;
    const rotateStartX = handle.x + handle.width / 2;
    const rotateStartY = handle.y + handle.height / 2;
    await page.mouse.move(rotateStartX, rotateStartY);
    await page.mouse.down();
    await page.mouse.move(rotateStartX + 95, rotateStartY + 65, { steps: 18 });
    await page.waitForTimeout(180);
    const rotating = await state();
    assert.ok(Math.abs(rotating.angle) > 10, 'rotation handle must change the angle');
    assertClose(rotating.center.x, rotateCenter.x, 'rotation pivot x', 2);
    assertClose(rotating.center.y, rotateCenter.y, 'rotation pivot y', 2);
    await page.mouse.up();
    await page.waitForTimeout(250);
    const rotated = await state();
    assert.ok(Math.abs(rotated.angle) > 10);
    assertClose(rotated.center.x, rotateCenter.x, 'final rotation centre x', 2);
    assertClose(rotated.center.y, rotateCenter.y, 'final rotation centre y', 2);

    // The curve is a real MotionPathPlugin path with four editable handles.
    await openTool(page, 'motion');
    await page.click('#motionPath');
    await page.waitForTimeout(250);
    const opened = await state();
    assert.ok(opened.path && opened.path.visible, 'curve must open');
    assert.ok(opened.path.d.startsWith('M '), 'curve must be a cubic path');
    assert.strictEqual(await page.isEnabled('#motionPathPlay'), true, 'play must be enabled for a visible path');
    const pathVisual = await visualGuard(page, 'studio-path-open');
    const pathD = opened.path.d;
    const c1 = await rect('.path-handle[data-point="c1"]');
    assert.ok(c1 && c1.width > 8, 'curve control handle must be visible');
    await page.mouse.move(c1.x + c1.width / 2, c1.y + c1.height / 2);
    await page.mouse.down();
    await page.mouse.move(c1.x + c1.width / 2 + 35, c1.y + c1.height / 2 - 24, { steps: 14 });
    await page.waitForTimeout(120);
    await page.mouse.up();
    await page.waitForTimeout(220);
    const edited = await state();
    assert.notStrictEqual(edited.path.d, pathD, 'editing a curve handle must change the path');
    assert.ok(edited.path.edits >= 1, 'curve edit count must increase');
    const handles = await page.evaluate(() => Array.from(document.querySelectorAll('.path-handle')).map((node) => ({
      key: node.dataset.point,
      display: getComputedStyle(node).display
    })));
    assert.strictEqual(handles.length, 4);
    assert.ok(handles.every((item) => item.display === 'block'), 'all four curve handles must be visible');

    await page.click('#motionPathPlay');
    await page.waitForTimeout(450);
    const playing = await state();
    assert.strictEqual(playing.path.playing, true, 'path playback must be running');
    await page.screenshot({ path: path.join(ARTIFACTS, 'studio-path-playing.png') });
    await page.waitForFunction(() => window.__studioInteraction.state().path.playing === false, null, { timeout: 5000 });
    const played = await state();
    assert.strictEqual(played.path.plays, 1);
    assertClose(played.path.lastEnd.x, played.path.points.end.x, 'path playback end x', 1);
    assertClose(played.path.lastEnd.y, played.path.points.end.y, 'path playback end y', 1);

    // Moving the layer after a curve exists must carry the curve with it. Without
    // this, PLAY PATH snaps the layer back to the curve's old origin.
    await page.click('#motionPathReset');
    await page.waitForTimeout(100);
    const beforeCarry = await state();
    const carryFrame = await rect('#selection-frame');
    const carryX = carryFrame.x + Math.min(28, carryFrame.width / 3);
    const carryY = carryFrame.y + Math.min(18, carryFrame.height / 2);
    await page.mouse.move(carryX, carryY);
    await page.mouse.down();
    await page.mouse.move(carryX + 28, carryY + 18, { steps: 12 });
    await page.waitForTimeout(120);
    await page.mouse.up();
    await page.waitForTimeout(220);
    const carried = await state();
    const carryDx = carried.center.x - beforeCarry.center.x;
    const carryDy = carried.center.y - beforeCarry.center.y;
    assert.ok(carryDx > 4 && carryDy > 4, 'carry check needs a real layer move');
    assertClose(carried.path.points.start.x, beforeCarry.path.points.start.x + carryDx, 'carried curve start x', 2);
    assertClose(carried.path.points.start.y, beforeCarry.path.points.start.y + carryDy, 'carried curve start y', 2);
    assertClose(carried.path.points.start.x, carried.center.x, 'curve start follows layer x', 2);
    assertClose(carried.path.points.start.y, carried.center.y, 'curve start follows layer y', 2);
    await page.click('#motionPathPlay');
    await page.waitForTimeout(140);
    const carryPlaying = await state();
    assertClose(carryPlaying.motion.lastStart.x, carried.center.x, 'play starts at moved layer x', 1);
    assertClose(carryPlaying.motion.lastStart.y, carried.center.y, 'play starts at moved layer y', 1);
    await page.waitForFunction(() => window.__studioInteraction.state().path.playing === false, null, { timeout: 5000 });
    const carryPlayed = await state();
    assertClose(carryPlayed.path.lastEnd.x, carryPlayed.path.points.end.x, 'carried playback end x', 1);
    assertClose(carryPlayed.path.lastEnd.y, carryPlayed.path.points.end.y, 'carried playback end y', 1);

    // Drag mid-animation. This must kill the tween, keep one Draggable, and start
    // the next gesture from the layer's real current position (not its pre-tween
    // transform or a stale Draggable offset).
    await page.click('#motionPathPlay');
    await page.waitForTimeout(180);
    const frameMidAnimation = await rect('#selection-frame');
    assert.ok(frameMidAnimation && frameMidAnimation.width > 10, 'frame must follow the animating layer');
    const pressX = frameMidAnimation.x + Math.min(28, frameMidAnimation.width / 3);
    const pressY = frameMidAnimation.y + Math.min(18, frameMidAnimation.height / 2);
    await page.mouse.move(pressX, pressY);
    await page.mouse.down();
    await page.waitForTimeout(40);
    await page.mouse.move(pressX + 32, pressY + 20, { steps: 12 });
    await page.waitForTimeout(120);
    await page.mouse.up();
    await page.waitForTimeout(220);
    const afterInterrupt = await state();
    assert.strictEqual(afterInterrupt.path.playing, false, 'drag must stop the path tween');
    assert.strictEqual(afterInterrupt.draggableCount, 1);
    assert.strictEqual(afterInterrupt.pathDraggableCount, 4);
    assert.ok(Number.isFinite(afterInterrupt.center.x) && Number.isFinite(afterInterrupt.center.y));
    const firstDragCentre = afterInterrupt.center;
    const secondFrame = await rect('#selection-frame');
    const secondStartX = secondFrame.x + Math.min(24, secondFrame.width / 3);
    const secondStartY = secondFrame.y + Math.min(16, secondFrame.height / 2);
    await page.mouse.move(secondStartX, secondStartY);
    await page.mouse.down();
    await page.mouse.move(secondStartX + 28, secondStartY + 16, { steps: 10 });
    await page.waitForTimeout(100);
    await page.mouse.up();
    await page.waitForTimeout(220);
    const secondDrag = await state();
    assertClose(secondDrag.motion.lastDragStart.center.x, firstDragCentre.x, 'next drag starts from actual x', 0.01);
    assertClose(secondDrag.motion.lastDragStart.center.y, firstDragCentre.y, 'next drag starts from actual y', 0.01);
    assert.strictEqual(secondDrag.draggableCount, 1);

    // Observer: pan from the empty stage gutter and wheel-zoom over the canvas.
    await page.evaluate(() => window.__studioInteraction.resetView());
    await openTool(page, 'motion');
    await page.click('#motionPath'); // hide the curve so its handles cannot intercept stage input
    await closeInspector(page);
    await page.waitForTimeout(150);
    const stageBox = await rect('#stage');
    const panStartX = stageBox.x + 9;
    const panStartY = stageBox.y + stageBox.height / 2;
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [{ x: panStartX, y: panStartY, id: 1 }]
    });
    for (let step = 1; step <= 12; step += 1) {
      await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x: panStartX + step * 2.7, y: panStartY + step * 1.8, id: 1 }]
      });
    }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await page.waitForTimeout(150);
    const panned = await state();
    assert.ok(panned.view.x > 4, 'Observer must pan the stage horizontally');
    assert.ok(panned.view.y > 2, 'Observer must pan the stage vertically');
    await page.evaluate(() => window.__studioInteraction.resetView());
    await page.waitForTimeout(100);
    const canvasBox = await rect('#stage-view');
    await page.mouse.move(canvasBox.x + 12, canvasBox.y + canvasBox.height - 12);
    await page.mouse.wheel(0, -240);
    await page.waitForTimeout(200);
    const zoomed = await state();
    assert.ok(zoomed.view.scale > 1.01, 'Observer must wheel-zoom the stage');
    await page.evaluate(() => window.__studioInteraction.resetView());
    await page.waitForTimeout(150);

    // A real touch drag on the layer frame must move the layer, not the stage.
    // Panning an empty stage is allowed to clear Fabric's selection; reselect the
    // line so this assertion is about the touch drag, not about pan behaviour.
    if ((await state()).selected == null) {
      await page.evaluate(() => {
        const canvas = window.StudioEngine.canvas;
        const target = canvas.getObjects().find((object) => object.text === 'MOVE');
        if (!target) { throw new Error('MOVE layer missing before touch drag'); }
        canvas.setActiveObject(target);
        canvas.requestRenderAll();
      });
      await page.waitForTimeout(180);
    }
    const touchFrame = await rect('#selection-frame');
    const touchBefore = await state();
    assert.ok(touchBefore.selected, 'touch drag needs a selected layer');
    const touchX = touchFrame.x + Math.min(28, touchFrame.width / 3);
    const touchY = touchFrame.y + Math.min(18, touchFrame.height / 2);
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [{ x: touchX, y: touchY, id: 2 }]
    });
    for (let step = 1; step <= 10; step += 1) {
      await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x: touchX + step * 2.6, y: touchY + step * 1.5, id: 2 }]
      });
    }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await page.waitForTimeout(220);
    const touchAfter = await state();
    assert.notStrictEqual(touchAfter.center.x, touchBefore.center.x, 'touch must move the selected layer');
    assert.strictEqual(touchAfter.view.x, 0, 'layer touch must not pan the stage');
    assert.deepStrictEqual(errors, []);

    // Reduced motion still uses the same editable path, but completes instantly.
    const reducedPage = await browser.newPage({
      viewport: { width: 390, height: 844 },
      deviceScaleFactor: 2,
      hasTouch: true,
      isMobile: true,
      reducedMotion: 'reduce'
    });
    const reducedErrors = [];
    reducedPage.on('console', (message) => {
      if (message.type() === 'error') { reducedErrors.push('console: ' + message.text()); }
    });
    reducedPage.on('pageerror', (error) => { reducedErrors.push('pageerror: ' + error.message); });
    await reducedPage.goto(`http://127.0.0.1:${port}/index.html`, { waitUntil: 'networkidle' });
    await reducedPage.waitForFunction(() => window.__studioInteraction && window.__studioMotion);
    await openTool(reducedPage, 'type');
    await reducedPage.click('#add');
    await closeInspector(reducedPage);
    await openTool(reducedPage, 'motion');
    await reducedPage.click('#motionPath');
    await reducedPage.click('#motionPathPlay');
    await reducedPage.waitForTimeout(120);
    const reduced = await reducedPage.evaluate(() => window.__studioInteraction.state());
    assert.strictEqual(reduced.reducedMotion, true);
    assert.strictEqual(reduced.path.playing, false, 'reduced motion must not leave a running path tween');
    assert.strictEqual(reduced.path.plays, 1);
    assertClose(reduced.path.lastEnd.x, reduced.path.points.end.x, 'reduced-motion path end x');
    assertClose(reduced.path.lastEnd.y, reduced.path.points.end.y, 'reduced-motion path end y');
    assert.deepStrictEqual(reducedErrors, []);
    await reducedPage.close();
    process.stdout.write(JSON.stringify({
      pass: true,
      snap: {
        target: { x: geometry.targetX, y: geometry.targetY },
        final: { x: afterDrag.bbox.left, y: afterDrag.bbox.top },
        guidesAtTarget: true,
        screenshot: 'artifacts/studio-snapped-guides.png'
      },
      rotation: { before: 0, during: rotating.angle, centreDrift: Math.hypot(rotating.center.x - rotateCenter.x, rotating.center.y - rotateCenter.y) },
      motion: { plays: played.path.plays, edits: edited.path.edits, endedAtPathEnd: true },
      visual: {
        snapped: snapVisual.updated ? 'updated' : { diffPixels: snapVisual.diffPixels, totalPixels: snapVisual.totalPixels },
        pathOpen: pathVisual.updated ? 'updated' : { diffPixels: pathVisual.diffPixels, totalPixels: pathVisual.totalPixels }
      },
      observer: { panX: panned.view.x, panY: panned.view.y, zoom: zoomed.view.scale },
      touch: { before: touchBefore.center, after: touchAfter.center },
      reducedMotion: { playing: reduced.path.playing, plays: reduced.path.plays },
      draggables: { frame: secondDrag.draggableCount, rotation: secondDrag.rotateDraggableCount, path: secondDrag.pathDraggableCount },
      errors
    }, null, 2) + '\n');
    // A one-line verdict for tools/check_all.py, which prints its summary rather than
    // the evidence JSON.
    process.stdout.write(
      snapVisual.updated || pathVisual.updated
        ? 'studio: baselines updated\n'
        : `studio: 2 baselines match (${(snapVisual.diffPixels || 0) + (pathVisual.diffPixels || 0)} px differ, 0 console errors)\n`
    );
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((error) => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
