/*
 * ADD Studio interaction: one GSAP layer over the existing Fabric canvas.
 * Draggable moves the selected text layer, snapping to real canvas/layer geometry;
 * the rotation ring is a Draggable trigger; MotionPathPlugin drives an editable curve;
 * Observer pans/zooms the stage without taking over layer drags.
 */
(function () {
  'use strict';

  var E = window.StudioEngine;
  if (!E || !window.gsap || !window.Draggable || !window.Observer || !window.MotionPathPlugin) {
    return;
  }

  var gsap = window.gsap;
  var Draggable = window.Draggable;
  var Observer = window.Observer;
  var MotionPath = window.MotionPathPlugin;
  gsap.registerPlugin(Draggable, Observer, MotionPath);

  var canvas = E.canvas;
  var W = E.W;
  var H = E.H;
  var STAGE_LABEL = 'TYPE';
  var SNAP = 9;
  var reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

  function $(id) { return document.getElementById(id); }
  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
  function normalAngle(angle) { return ((angle + 180) % 360 + 360) % 360 - 180; }
  function copyPoint(point) { return { x: point.x, y: point.y }; }

  var stage = $('stage');
  var view = $('stage-view');
  var frame = $('selection-frame');
  var frameTag = $('selection-tag');
  var ring = $('rotate-ring');
  var rotateHandle = $('rotate-handle');
  var guides = $('alignment-guides');
  var guideV = guides ? guides.querySelector('.guide-v') : null;
  var guideH = guides ? guides.querySelector('.guide-h') : null;
  var pathSvg = $('motion-path');
  var pathLine = $('motion-path-line');
  var pathHandleEls = {};
  Array.prototype.forEach.call(document.querySelectorAll('.path-handle'), function (el) {
    pathHandleEls[el.getAttribute('data-point')] = el;
  });

  var current = null;
  var frameDrag = null;
  var rotateDrag = null;
  var pathDrags = [];
  var pathStates = new WeakMap();
  var motionTween = null;
  var motionInfo = { plays: 0, lastStart: null, lastEnd: null, edits: 0, lastDragStart: null };
  var dragging = false;
  var rotating = false;
  var activePathHandle = null;
  var startDrag = null;
  var startPathPoint = null;
  var rotationPivot = null;
  var targetsX = [];
  var targetsY = [];
  var snapX = null;
  var snapY = null;
  var lastGuides = { x: null, y: null, activeX: false, activeY: false };
  var viewState = { x: 0, y: 0, scale: 1 };
  var panMode = false;

  function scale() {
    return (canvas.wrapperEl && canvas.wrapperEl.clientWidth ? canvas.wrapperEl.clientWidth : W) / W;
  }

  function origin() {
    return {
      x: canvas.wrapperEl ? canvas.wrapperEl.offsetLeft : 0,
      y: canvas.wrapperEl ? canvas.wrapperEl.offsetTop : 0
    };
  }

  function toStage(x, y) {
    var s = scale();
    var o = origin();
    return { x: o.x + x * s, y: o.y + y * s };
  }

  function boxOf(object) {
    var box = object.getBoundingRect();
    return { left: box.left, top: box.top, width: box.width, height: box.height };
  }

  function currentPath() {
    return current ? pathStates.get(current) : null;
  }

  function pathData(state) {
    return 'M ' + state.start.x + ' ' + state.start.y +
      ' C ' + state.c1.x + ' ' + state.c1.y + ', ' +
      state.c2.x + ' ' + state.c2.y + ', ' +
      state.end.x + ' ' + state.end.y;
  }

  function createPath(object) {
    var center = object.getCenterPoint();
    var start = { x: clamp(center.x, 30, W - 30), y: clamp(center.y, 30, H - 30) };
    var end = {
      x: clamp(start.x + 215, 45, W - 45),
      y: clamp(start.y + 165, 45, H - 45)
    };
    if (end.x < start.x + 80) { end.x = clamp(start.x - 215, 45, W - 45); }
    return {
      start: start,
      c1: { x: clamp(start.x + 92, 24, W - 24), y: clamp(start.y - 105, 24, H - 24) },
      c2: { x: clamp(end.x - 92, 24, W - 24), y: clamp(end.y + 92, 24, H - 24) },
      end: end,
      visible: true
    };
  }

  /**
   * Candidate X/Y values from the canvas and every other selectable layer. A layer
   * contributes its two edges and its centre, so snapping means the same thing for
   * a text box, a group and the canvas itself.
   */
  function prepareTargets() {
    var xs = [0, W / 2, W];
    var ys = [0, H / 2, H];
    var overlay = E.getOverlay();
    canvas.getObjects().forEach(function (object) {
      if (object === current || object === overlay || object.visible === false) { return; }
      var box = boxOf(object);
      xs.push(box.left, box.left + box.width / 2, box.left + box.width);
      ys.push(box.top, box.top + box.height / 2, box.top + box.height);
    });
    function unique(values) {
      var seen = {};
      return values.filter(function (value) {
        var key = value.toFixed(3);
        if (seen[key]) { return false; }
        seen[key] = true;
        return true;
      });
    }
    targetsX = unique(xs);
    targetsY = unique(ys);
  }

  function snapAxis(axis, rawDelta) {
    if (!startDrag) { return { delta: 0, target: null }; }
    var box = startDrag.box;
    var base = axis === 'x' ? box.left : box.top;
    var size = axis === 'x' ? box.width : box.height;
    var proposed = base + rawDelta;
    var points = [proposed, proposed + size / 2, proposed + size];
    var targets = axis === 'x' ? targetsX : targetsY;
    var best = SNAP + 0.001;
    var correction = 0;
    var target = null;
    points.forEach(function (point) {
      targets.forEach(function (candidate) {
        var difference = candidate - point;
        if (Math.abs(difference) < best) {
          best = Math.abs(difference);
          correction = difference;
          target = candidate;
        }
      });
    });
    if (target === null || Math.abs(correction) > SNAP) {
      return { delta: 0, target: null };
    }
    return { delta: correction, target: target };
  }

  function hideGuides() {
    if (guides) { guides.classList.remove('active'); }
    if (guideV) { guideV.style.display = 'none'; }
    if (guideH) { guideH.style.display = 'none'; }
  }

  function showGuides() {
    var s = scale();
    var o = origin();
    var activeX = snapX !== null;
    var activeY = snapY !== null;
    if (guideV) {
      guideV.style.display = activeX ? 'block' : 'none';
      if (activeX) { guideV.style.left = (o.x + snapX * s) + 'px'; }
    }
    if (guideH) {
      guideH.style.display = activeY ? 'block' : 'none';
      if (activeY) { guideH.style.top = (o.y + snapY * s) + 'px'; }
    }
    if (guides) {
      guides.classList.toggle('active', activeX || activeY);
      guides.setAttribute('data-active', activeX || activeY ? 'true' : 'false');
    }
    lastGuides = {
      x: snapX,
      y: snapY,
      activeX: activeX,
      activeY: activeY
    };
  }

  function hideFrame() {
    if (frame) { frame.style.display = 'none'; }
    if (ring) { ring.style.display = 'none'; }
    hideGuides();
  }

  function hidePath() {
    if (pathSvg) { pathSvg.style.display = 'none'; }
    Object.keys(pathHandleEls).forEach(function (key) {
      pathHandleEls[key].style.display = 'none';
    });
  }

  function syncPath(skipHandle) {
    var state = currentPath();
    if (!current || !state || !state.visible) {
      hidePath();
      return;
    }
    if (pathSvg) { pathSvg.style.display = 'block'; }
    if (pathLine) { pathLine.setAttribute('d', pathData(state)); }
    Object.keys(pathHandleEls).forEach(function (key) {
      var el = pathHandleEls[key];
      var point = state[key];
      if (!point) { return; }
      el.style.display = 'block';
      if (skipHandle && el === skipHandle) { return; }
      var screen = toStage(point.x, point.y);
      el.style.left = screen.x + 'px';
      el.style.top = screen.y + 'px';
    });
  }

  function syncFrame() {
    if (!current) {
      hideFrame();
      return;
    }
    var box = boxOf(current);
    var s = scale();
    var o = origin();
    if (frame) {
      frame.style.display = 'block';
      frame.style.left = (o.x + box.left * s) + 'px';
      frame.style.top = (o.y + box.top * s) + 'px';
      frame.style.width = Math.max(18, box.width * s) + 'px';
      frame.style.height = Math.max(18, box.height * s) + 'px';
    }
    if (frameTag) {
      var label = (current.text || STAGE_LABEL).trim().replace(/\s+/g, ' ').slice(0, 16).toUpperCase();
      frameTag.textContent = label + (current.angle ? ' · ' + Math.round(normalAngle(current.angle)) + '°' : '');
    }
    if (ring) {
      var center = current.getCenterPoint();
      var radius = Math.min(130, Math.max(46, Math.max(box.width, box.height) * s * 0.56 + 24));
      ring.style.display = 'block';
      ring.style.left = (o.x + center.x * s - radius) + 'px';
      ring.style.top = (o.y + center.y * s - radius) + 'px';
      ring.style.width = (radius * 2) + 'px';
      ring.style.height = (radius * 2) + 'px';
      gsap.set(ring, { rotation: current.angle || 0 });
    }
    syncPath();
  }

  function syncControls() {
    var button = $('motionPath');
    var play = $('motionPathPlay');
    var reset = $('motionPathReset');
    var state = currentPath();
    var has = !!current;
    var visible = !!(state && state.visible);
    if (button) {
      button.disabled = !has;
      button.setAttribute('aria-pressed', visible ? 'true' : 'false');
      button.textContent = visible ? 'Curve on' : 'Curve';
    }
    if (play) { play.disabled = !has || !visible || !!motionTween; }
    if (reset) { reset.disabled = !has; }
  }

  function stopMotion() {
    if (motionTween) {
      motionTween.kill();
      motionTween = null;
    }
    gsap.killTweensOf(frame);
    gsap.killTweensOf(ring);
    if (current) {
      var state = pathStates.get(current);
      if (state) { state.playing = false; }
    }
    syncControls();
  }

  function prepareObject(object) {
    if (!(object instanceof fabric.AddText)) { return; }
    object.set({
      hasControls: false,
      hasBorders: false,
      lockMovementX: true,
      lockMovementY: true,
      selectable: true,
      evented: true
    });
    object.setCoords();
  }

  function parkFrame() {
    if (frameDrag) {
      frameDrag.x = 0;
      frameDrag.y = 0;
    }
    gsap.set(frame, { x: 0, y: 0 });
    if (current) { syncFrame(); }
  }

  function refreshSelection() {
    var next = E.activeText();
    if (next !== current) { stopMotion(); }
    current = next;
    if (current) { prepareObject(current); }
    dragging = false;
    rotating = false;
    activePathHandle = null;
    if (frameDrag) {
      frameDrag.x = 0;
      frameDrag.y = 0;
    }
    gsap.set(frame, { x: 0, y: 0 });
    hideGuides();
    if (!current) {
      hideFrame();
      hidePath();
      syncControls();
      return;
    }
    syncFrame();
    syncControls();
  }

  function finishMovement() {
    if (!current || !frameDrag) { return; }
    dragging = false;
    hideGuides();
    var finalBox = boxOf(current);
    var finalLeft = current.left;
    var finalTop = current.top;
    frameDrag.x = 0;
    frameDrag.y = 0;
    gsap.set(frame, { x: 0, y: 0 });
    syncFrame();
    lastGuides = { x: snapX, y: snapY, activeX: snapX !== null, activeY: snapY !== null };
    motionInfo.lastMove = {
      left: finalLeft,
      top: finalTop,
      center: current.getCenterPoint(),
      box: finalBox,
      snapX: snapX,
      snapY: snapY
    };
    canvas.fire('object:modified', { target: current });
    E.syncPanel();
    syncControls();
  }

  /* ------------------------------------------------------------------ *
   * Draggable: one frame for the selected text layer. The object is the
   * source of truth; snapping updates left/top before the frame is parked.
   * ------------------------------------------------------------------ */
  frameDrag = Draggable.create(frame, {
    type: 'x,y',
    inertia: false,
    allowNativeTouchScrolling: false,
    dragResistance: 0,
    edgeResistance: 0.6,
    minimumMovement: 3,
    onPress: function () {
      if (!current) { return false; }
      stopMotion();
      parkFrame();
      startDrag = {
        left: current.left,
        top: current.top,
        box: boxOf(current)
      };
      motionInfo.lastDragStart = {
        left: current.left,
        top: current.top,
        center: current.getCenterPoint(),
        box: startDrag.box
      };
      prepareTargets();
      snapX = null;
      snapY = null;
      dragging = true;
      this.x = 0;
      this.y = 0;
      gsap.set(frame, { x: 0, y: 0 });
    },
    onDrag: function () {
      if (!current || !startDrag) { return; }
      var s = scale();
      var rawX = this.x / s;
      var rawY = this.y / s;
      var sx = snapAxis('x', rawX);
      var sy = snapAxis('y', rawY);
      snapX = sx.target;
      snapY = sy.target;
      var dx = rawX + sx.delta;
      var dy = rawY + sy.delta;
      current.set({
        left: startDrag.left + dx,
        top: startDrag.top + dy
      });
      current.setCoords();
      canvas.requestRenderAll();
      gsap.set(frame, { x: dx * s, y: dy * s });
      showGuides();
    },
    onRelease: finishMovement
  })[0];

  /* Rotation is a real Draggable rotation ring: its centre is the object's
   * centre, and its trigger is the visible handle at the top. */
  rotateDrag = Draggable.create(ring, {
    type: 'rotation',
    trigger: rotateHandle,
    inertia: false,
    onPress: function () {
      if (!current) { return false; }
      stopMotion();
      rotationPivot = current.getCenterPoint();
      rotating = true;
      syncFrame();
    },
    onDrag: function () {
      if (!current) { return; }
      var angle = normalAngle(this.rotation);
      current.set('angle', angle);
      current.setCoords();
      // AddText's left/top origin is not its visual centre. Keep the layer
      // rotating in place by pinning its pre-drag centre after each angle step.
      if (rotationPivot) {
        var rotatedCenter = current.getCenterPoint();
        current.set({
          left: current.left + (rotationPivot.x - rotatedCenter.x),
          top: current.top + (rotationPivot.y - rotatedCenter.y)
        });
        current.setCoords();
      }
      canvas.requestRenderAll();
      if (frameTag) {
        frameTag.textContent = ((current.text || STAGE_LABEL).trim().toUpperCase().slice(0, 16)) + ' · ' + Math.round(angle) + '°';
      }
    },
    onRelease: function () {
      if (!current) { return; }
      rotating = false;
      rotationPivot = null;
      syncFrame();
      motionInfo.lastRotation = normalAngle(current.angle);
      E.syncPanel();
    }
  })[0];

  /* ------------------------------------------------------------------ *
   * MotionPath: an editable cubic curve with four visible handles. The
   * layer is driven by MotionPathPlugin.getPositionOnPath, not a fake dot.
   * ------------------------------------------------------------------ */
  function togglePath() {
    if (!current) { E.flash('Select a line first'); return; }
    var state = pathStates.get(current);
    if (!state) {
      state = createPath(current);
      pathStates.set(current, state);
    } else {
      state.visible = !state.visible;
    }
    if (state.visible) { syncPath(); } else { hidePath(); }
    syncControls();
  }

  function resetPath() {
    if (!current) { return; }
    var state = createPath(current);
    state.visible = true;
    pathStates.set(current, state);
    motionInfo.edits += 1;
    syncPath();
    syncControls();
  }

  function playPath() {
    if (!current) { return; }
    var state = pathStates.get(current);
    if (!state || !state.visible) { return; }
    stopMotion();
    var raw = MotionPath.getRawPath(pathData(state));
    var proxy = { progress: 0 };
    motionInfo.plays += 1;
    motionInfo.lastStart = current.getCenterPoint();
    if (reduce) {
      var endCenter = current.getCenterPoint();
      current.set({
        left: current.left + (state.end.x - endCenter.x),
        top: current.top + (state.end.y - endCenter.y)
      });
      current.setCoords();
      canvas.requestRenderAll();
      state.playing = false;
      motionInfo.lastEnd = current.getCenterPoint();
      syncFrame();
      syncControls();
      return;
    }
    state.playing = true;
    syncControls();
    motionTween = gsap.to(proxy, {
      progress: 1,
      duration: reduce ? 0 : 1.4,
      ease: 'power2.inOut',
      onUpdate: function () {
        if (current !== this._object) { return; }
        var point = MotionPath.getPositionOnPath(raw, proxy.progress);
        var center = current.getCenterPoint();
        current.set({
          left: current.left + (point.x - center.x),
          top: current.top + (point.y - center.y)
        });
        current.setCoords();
        canvas.requestRenderAll();
        syncFrame();
      },
      onComplete: function () {
        motionTween = null;
        state.playing = false;
        motionInfo.lastEnd = current ? current.getCenterPoint() : null;
        syncFrame();
        syncControls();
      }
    });
    motionTween._object = current;
  }

  function beginPathDrag(key, drag) {
    var state = currentPath();
    if (!current || !state || !state.visible) { return false; }
    stopMotion();
    activePathHandle = key;
    startPathPoint = copyPoint(state[key]);
    gsap.set(pathHandleEls[key], { x: 0, y: 0 });
    drag.x = 0;
    drag.y = 0;
    return true;
  }

  function dragPath(key, drag) {
    var state = currentPath();
    if (!current || !state || !activePathHandle) { return; }
    var s = scale();
    state[key] = {
      x: clamp(startPathPoint.x + drag.x / s, 4, W - 4),
      y: clamp(startPathPoint.y + drag.y / s, 4, H - 4)
    };
    syncPath(pathHandleEls[key]);
  }

  function endPathDrag(key, drag) {
    drag.x = 0;
    drag.y = 0;
    gsap.set(pathHandleEls[key], { x: 0, y: 0 });
    activePathHandle = null;
    motionInfo.edits += 1;
    syncPath();
    syncControls();
  }

  Object.keys(pathHandleEls).forEach(function (key) {
    var handle = pathHandleEls[key];
    var drag = Draggable.create(handle, {
      type: 'x,y',
      inertia: false,
      allowNativeTouchScrolling: false,
      minimumMovement: 2,
      onPress: function () { beginPathDrag(key, this); },
      onDrag: function () { dragPath(key, this); },
      onRelease: function () { endPathDrag(key, this); }
    })[0];
    pathDrags.push(drag);
  });

  $('motionPath').addEventListener('click', togglePath);
  $('motionPathReset').addEventListener('click', resetPath);
  $('motionPathPlay').addEventListener('click', playPath);

  /* ------------------------------------------------------------------ *
   * Observer: background drag pans the fitted stage; wheel zooms it. A
   * pointer that starts on the frame, rotation handle or path handle is
   * explicitly ignored so Draggable owns it.
   * ------------------------------------------------------------------ */
  function isStudioUi(event) {
    var target = event && event.target;
    return !!(target && target.closest && target.closest('#studio-ui'));
  }

  function applyView() {
    gsap.set(view, {
      x: viewState.x,
      y: viewState.y,
      scale: viewState.scale
    });
    view.setAttribute('data-zoom', viewState.scale.toFixed(2));
    canvas.calcOffset();
  }

  function resetView() {
    viewState = { x: 0, y: 0, scale: 1 };
    applyView();
    syncFrame();
  }

  Observer.create({
    target: stage,
    type: 'wheel,touch,pointer',
    dragMinimum: 5,
    tolerance: 2,
    onPress: function (self) {
      panMode = !isStudioUi(self.event);
    },
    onDrag: function (self) {
      if (!panMode) { return; }
      viewState.x += self.deltaX;
      viewState.y += self.deltaY;
      applyView();
    },
    onRelease: function () {
      panMode = false;
    },
    onWheel: function (self) {
      if (isStudioUi(self.event)) { return; }
      var step = self.deltaY > 0 ? 0.94 : 1.06;
      viewState.scale = clamp(viewState.scale * step, 0.8, 1.4);
      applyView();
    }
  });

  /* ------------------------------------------------------------------ *
   * Wiring and public state for the headless driver.
   * ------------------------------------------------------------------ */
  canvas.on('selection:created', refreshSelection);
  canvas.on('selection:updated', refreshSelection);
  canvas.on('selection:cleared', refreshSelection);
  canvas.on('object:added', function (event) { prepareObject(event.target); });
  canvas.on('object:modified', function () { if (current) { syncFrame(); } });

  var textInput = $('text');
  if (textInput) {
    textInput.addEventListener('input', function () {
      if (current) {
        E.syncPanel();
        requestAnimationFrame(syncFrame);
      }
    });
  }

  var resizeFrame = 0;
  function scheduleSync() {
    if (resizeFrame) { return; }
    resizeFrame = requestAnimationFrame(function () {
      resizeFrame = 0;
      syncFrame();
      syncPath();
    });
  }
  window.addEventListener('resize', scheduleSync);
  if (window.ResizeObserver) {
    new ResizeObserver(scheduleSync).observe(stage);
  }

  canvas.getObjects().forEach(prepareObject);
  refreshSelection();
  applyView();

  function publishPlugins() {
    if (window.__studioMotion) {
      window.__studioMotion.plugins = ['core', 'Draggable', 'Observer', 'MotionPathPlugin'];
      window.__studioMotion.interaction = true;
    }
  }
  publishPlugins();
  document.addEventListener('DOMContentLoaded', publishPlugins);
  document.documentElement.dataset.interaction = 'draggable-observer-motionpath';

  window.__studioInteraction = {
    state: function () {
      var state = currentPath();
      return {
        selected: current ? current.text : null,
        left: current ? current.left : null,
        top: current ? current.top : null,
        angle: current ? normalAngle(current.angle) : null,
        center: current ? current.getCenterPoint() : null,
        bbox: current ? boxOf(current) : null,
        guides: {
          active: !!(guides && guides.classList.contains('active')),
          x: snapX,
          y: snapY,
          last: lastGuides
        },
        path: state ? {
          d: pathData(state),
          points: {
            start: copyPoint(state.start),
            c1: copyPoint(state.c1),
            c2: copyPoint(state.c2),
            end: copyPoint(state.end)
          },
          visible: state.visible,
          playing: !!motionTween,
          plays: motionInfo.plays,
          edits: motionInfo.edits,
          lastStart: motionInfo.lastStart,
          lastEnd: motionInfo.lastEnd
        } : null,
        view: { x: viewState.x, y: viewState.y, scale: viewState.scale },
        motion: {
          plays: motionInfo.plays,
          lastStart: motionInfo.lastStart,
          lastEnd: motionInfo.lastEnd,
          lastMove: motionInfo.lastMove || null,
          lastDragStart: motionInfo.lastDragStart || null,
          lastRotation: motionInfo.lastRotation == null ? null : motionInfo.lastRotation
        },
        draggableCount: frameDrag ? 1 : 0,
        rotateDraggableCount: rotateDrag ? 1 : 0,
        pathDraggableCount: pathDrags.length,
        reducedMotion: reduce
      };
    },
    resetView: resetView,
    togglePath: togglePath,
    playPath: playPath
  };
})();
