/* ADD Studio engine shell: Fabric+ canvas with live effects, fast-drag and native export. */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var W = 540;
  var H = 675;

  fabric.Object.prototype.set({
    cornerColor: '#dd4124',
    cornerStrokeColor: '#f2eee3',
    borderColor: '#dd4124',
    cornerSize: 16,
    transparentCorners: false,
    padding: 4
  });

  var canvas = new fabric.Canvas('c', {
    width: W,
    height: H,
    backgroundColor: '#f2eee3',
    preserveObjectStacking: true,
    selection: true,
    allowTouchScrolling: false,
    enableRetinaScaling: false
  });

  var stageEl = $('stage');

  /** Fit the fixed 540x675 document into the phone's available stage. The Fabric
   *  coordinate space stays 540x675, so pointer maths and the 2x export are unchanged;
   *  only the wrapper and its two canvases are scaled in CSS. */
  function fitCanvas() {
    if (!stageEl || !canvas.wrapperEl) { return; }
    var rect = stageEl.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) { return; }
    var scale = Math.min((rect.width - 24) / W, (rect.height - 24) / H);
    var width = Math.max(1, Math.round(W * scale));
    var height = Math.max(1, Math.round(H * scale));
    canvas.wrapperEl.style.width = width + 'px';
    canvas.wrapperEl.style.height = height + 'px';
    var stageView = stageEl.querySelector('#stage-view');
    if (stageView) {
      stageView.style.width = width + 'px';
      stageView.style.height = height + 'px';
    }
    [canvas.lowerCanvasEl, canvas.upperCanvasEl].forEach(function (element) {
      if (!element) { return; }
      element.style.width = '100%';
      element.style.height = '100%';
    });
    canvas.calcOffset();
  }

  var statusTimer = 0;
  var overlay = null;
  var overlayKind = 'none';
  var overlaySeed = 1;
  var overlayEditing = false;
  var overlayRequest = 0;

  function flash(message) {
    var label = $('selection');
    label.textContent = message;
    clearTimeout(statusTimer);
    statusTimer = setTimeout(syncPanel, 1400);
  }

  function activeText() {
    var object = canvas.getActiveObject();
    return object instanceof fabric.AddText ? object : null;
  }

  function selectedGroup() {
    var object = canvas.getActiveObject();
    return object && object.type === 'group' ? object : null;
  }

  function overlayDefaults(kind) {
    return (window.overlayDefaults && window.overlayDefaults[kind]) || { blend: 'screen', opacity: 0.5 };
  }

  function raiseOverlay() {
    if (overlay) { canvas.bringToFront(overlay); }
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function normalAngle(angle) {
    return ((Math.round(angle || 0) + 180) % 360 + 360) % 360 - 180;
  }

  function overlayTransform() {
    if (!overlay) {
      return { x: 0, y: 0, scale: 100, angle: 0 };
    }
    return {
      x: (overlay.left - W / 2) / W * 100,
      y: (overlay.top - H / 2) / H * 100,
      scale: (Math.abs(overlay.scaleX) + Math.abs(overlay.scaleY)) / 2 * 100,
      angle: normalAngle(overlay.angle)
    };
  }

  function formatOverlayNumber(value, suffix) {
    var rounded = Math.round(value * 10) / 10;
    return (rounded % 1 === 0 ? rounded.toFixed(0) : rounded.toFixed(1)) + suffix;
  }

  function syncOverlayTransform() {
    if (!overlay) { return; }
    var transform = overlayTransform();
    $('overlayX').value = clamp(transform.x, -100, 100);
    $('overlayY').value = clamp(transform.y, -100, 100);
    $('overlayScale').value = clamp(transform.scale, 25, 250);
    $('overlayRotate').value = transform.angle;
    $('overlayXValue').textContent = formatOverlayNumber(transform.x, '%');
    $('overlayYValue').textContent = formatOverlayNumber(transform.y, '%');
    $('overlayScaleValue').textContent = formatOverlayNumber(transform.scale, '%');
    $('overlayRotateValue').textContent = formatOverlayNumber(transform.angle, '\u00b0');
  }

  function setOverlayTransform(transform) {
    if (!overlay) { return; }
    overlay.set({
      left: W / 2 + (transform.x / 100) * W,
      top: H / 2 + (transform.y / 100) * H,
      scaleX: transform.scale / 100,
      scaleY: transform.scale / 100,
      angle: transform.angle
    });
    overlay.setCoords();
    raiseOverlay();
    canvas.requestRenderAll();
    syncOverlayTransform();
  }

  function syncOverlayPanel() {
    var has = !!overlay;
    $('overlayKind').value = overlayKind;
    $('overlayOpacity').disabled = !has;
    $('overlayOpacity').value = has ? Math.round(overlay.opacity * 100) : 55;
    $('overlayValue').textContent = $('overlayOpacity').value;
    $('overlayBlend').disabled = !has;
    $('overlayBlend').value = has ? overlay.globalCompositeOperation : 'screen';
    $('overlayPlace').disabled = !has;
    $('overlayPlace').setAttribute('aria-pressed', overlayEditing ? 'true' : 'false');
    $('overlayPlace').textContent = overlayEditing ? 'Done' : 'Place';
    $('overlayShuffle').disabled = !has;
    $('overlayReset').disabled = !has;
    $('overlayRemove').disabled = !has;
    $('overlayTransform').hidden = !has;
    if (has) { syncOverlayTransform(); }
  }

  function setOverlayEditing(on) {
    overlayEditing = !!on && !!overlay;
    if (overlay) {
      overlay.set({
        selectable: overlayEditing,
        evented: overlayEditing,
        hasControls: overlayEditing,
        hasBorders: overlayEditing
      });
      overlay.setCoords();
      raiseOverlay();
      canvas.selection = !overlayEditing;
      if (overlayEditing) {
        canvas.setActiveObject(overlay);
      } else if (canvas.getActiveObject() === overlay) {
        canvas.discardActiveObject();
      }
      canvas.requestRenderAll();
    } else {
      canvas.selection = true;
    }
    $('overlayPlace').disabled = !overlay;
    $('overlayPlace').setAttribute('aria-pressed', overlayEditing ? 'true' : 'false');
    $('overlayPlace').textContent = overlayEditing ? 'Done' : 'Place';
    syncPanel();
    if (overlayEditing) { flash('Drag the overlay; handles scale and rotate'); }
  }

  function applyOverlay(kind, seed) {
    if (!kind || kind === 'none') {
      removeOverlay();
      return;
    }
    var data = window.makeOverlay(kind, seed, W, H);
    if (!data) {
      return;
    }
    var defaults = overlayDefaults(kind);
    var previous = overlay ? overlayTransform() : { x: 0, y: 0, scale: 100, angle: 0 };
    var sameKind = !!overlay && kind === overlayKind;
    var opacity = sameKind ? overlay.opacity : defaults.opacity;
    var blend = sameKind ? overlay.globalCompositeOperation : defaults.blend;
    var request = ++overlayRequest;
    fabric.Image.fromURL(data, function (image) {
      if (request !== overlayRequest) { return; }
      canvas.discardActiveObject();
      if (overlay) { canvas.remove(overlay); }
      image.set({
        left: W / 2,
        top: H / 2,
        originX: 'center',
        originY: 'center',
        selectable: false,
        evented: false,
        hasControls: false,
        hasBorders: false,
        lockScalingFlip: true,
        cornerSize: 22,
        touchCornerSize: 34,
        opacity: opacity,
        globalCompositeOperation: blend
      });
      canvas.add(image);
      canvas.bringToFront(image);
      overlay = image;
      overlayKind = kind;
      overlaySeed = seed;
      setOverlayTransform(previous);
      if (overlayEditing) { setOverlayEditing(true); }
      syncOverlayPanel();
      syncPanel();
      canvas.requestRenderAll();
    });
  }

  function removeOverlay() {
    overlayRequest += 1;
    setOverlayEditing(false);
    if (overlay) { canvas.remove(overlay); }
    overlay = null;
    overlayKind = 'none';
    canvas.discardActiveObject();
    canvas.selection = true;
    canvas.requestRenderAll();
    syncOverlayPanel();
    syncPanel();
  }

  function addText(text, options, select) {
    if (overlayEditing) { setOverlayEditing(false); }
    var settings = Object.assign({
      left: 60,
      top: 180,
      width: 420,
      fontSize: 84,
      fontFamily: 'Poppins, system-ui, sans-serif',
      fontWeight: '600',
      textAlign: 'center',
      startColour: '#15120e',
      endColour: '#dd4124',
      gradientOn: true,
      gradientAngle: 90,
      outlineOn: false,
      outlineColour: '#15120e',
      outlineWidth: 6,
      shadowOn: true,
      blendMode: 'source-over'
    }, options || {});
    var object = new fabric.AddText(text, settings);
    canvas.add(object);
    raiseOverlay();
    if (select !== false) {
      canvas.setActiveObject(object);
    }
    canvas.requestRenderAll();
    return object;
  }

  function syncPanel() {
    var object = activeText();
    var group = selectedGroup();
    var overlaySelected = !!overlay && canvas.getActiveObject() === overlay;
    $('selection').textContent = overlaySelected ? 'Overlay selected'
      : object ? 'Selected: ' + object.text.slice(0, 18)
        : (group ? 'Group selected' : (canvas.getActiveObjects().length > 1
          ? canvas.getActiveObjects().length + ' lines selected' : 'Nothing selected'));

    var disabled = !object;
    ['text', 'gradient', 'start', 'end', 'angle', 'outline', 'outlineColour',
      'outlineWidth', 'shadow', 'blend'].forEach(function (id) {
      $(id).disabled = disabled;
    });
    $('duplicate').disabled = !object && !group;
    $('remove').disabled = !object && !group;

    if (!object) {
      return;
    }
    $('text').value = object.text;
    $('gradient').checked = object.gradientOn;
    $('start').value = object.startColour;
    $('end').value = object.endColour;
    $('angle').value = object.gradientAngle;
    $('angleValue').innerHTML = Math.round(object.gradientAngle) + '&deg;';
    $('outline').checked = object.outlineOn;
    $('outlineColour').value = object.outlineColour;
    $('outlineWidth').value = object.outlineWidth;
    $('outlineValue').textContent = Math.round(object.outlineWidth);
    $('shadow').checked = object.shadowOn;
    $('blend').value = object.blendMode;
  }

  function update(mutator) {
    var object = activeText();
    if (!object) {
      return;
    }
    mutator(object);
    object.applyEffects();
    canvas.requestRenderAll();
  }

  $('text').addEventListener('input', function (event) {
    update(function (object) {
      object.set('text', event.target.value);
      object.initDimensions();
      object.setCoords();
    });
  });
  $('gradient').addEventListener('change', function (event) {
    update(function (object) { object.gradientOn = event.target.checked; });
  });
  $('start').addEventListener('input', function (event) {
    update(function (object) { object.startColour = event.target.value; });
  });
  $('end').addEventListener('input', function (event) {
    update(function (object) { object.endColour = event.target.value; });
  });
  $('angle').addEventListener('input', function (event) {
    $('angleValue').innerHTML = event.target.value + '&deg;';
    update(function (object) { object.gradientAngle = Number(event.target.value); });
  });
  $('outline').addEventListener('change', function (event) {
    update(function (object) { object.outlineOn = event.target.checked; });
  });
  $('outlineColour').addEventListener('input', function (event) {
    update(function (object) { object.outlineColour = event.target.value; });
  });
  $('outlineWidth').addEventListener('input', function (event) {
    $('outlineValue').textContent = event.target.value;
    update(function (object) { object.outlineWidth = Number(event.target.value); });
  });
  $('shadow').addEventListener('change', function (event) {
    update(function (object) { object.shadowOn = event.target.checked; });
  });
  $('blend').addEventListener('change', function (event) {
    update(function (object) { object.blendMode = event.target.value; });
  });

  $('add').addEventListener('click', function () {
    var count = canvas.getObjects().filter(function (o) { return o instanceof fabric.AddText; }).length;
    addText('NEW LINE', { top: 120 + (count % 4) * 110, fontSize: 64 });
    syncPanel();
  });

  $('duplicate').addEventListener('click', function () {
    var object = canvas.getActiveObject();
    if (!object) { return; }
    object.clone(function (copy) {
      copy.set({ left: object.left + 24, top: object.top + 24, angle: object.angle });
      canvas.add(copy);
      raiseOverlay();
      canvas.setActiveObject(copy);
      canvas.requestRenderAll();
      syncPanel();
    });
  });

  $('remove').addEventListener('click', function () {
    canvas.getActiveObjects().forEach(function (object) { canvas.remove(object); });
    canvas.discardActiveObject();
    canvas.requestRenderAll();
    syncPanel();
  });

  $('group').addEventListener('click', function () {
    var objects = canvas.getObjects().filter(function (o) { return o instanceof fabric.AddText; });
    if (objects.length < 2) { flash('Add two lines first'); return; }
    objects.forEach(function (object) { canvas.remove(object); });
    var group = new fabric.Group(objects, { left: 40, top: 150 });
    canvas.add(group);
    raiseOverlay();
    canvas.setActiveObject(group);
    canvas.requestRenderAll();
    syncPanel();
  });

  $('ungroup').addEventListener('click', function () {
    var group = selectedGroup();
    if (!group) { flash('Select a group first'); return; }
    var items = group.getObjects();
    var left = group.left;
    var top = group.top;
    canvas.remove(group);
    items.forEach(function (object) {
      object.set({ left: object.left + left, top: object.top + top });
      canvas.add(object);
    });
    canvas.setActiveObject(new fabric.ActiveSelection(items, { canvas: canvas }));
    raiseOverlay();
    canvas.requestRenderAll();
    syncPanel();
  });

  /* Fast mode: the expensive text passes return when the gesture ends. */
  var fast = null;
  function fastOn(object) {
    if (!object || fast || !(object instanceof fabric.AddText)) { return; }
    fast = { object: object, shadow: object.shadow, fill: object.fill };
    object.set('shadow', null);
    if (object.gradientOn) { object.set('fill', object.startColour); }
    object.dirty = true;
  }
  function fastOff() {
    if (!fast) { return; }
    fast.object.set('shadow', fast.shadow);
    fast.object.set('fill', fast.fill);
    fast.object.dirty = true;
    fast = null;
    canvas.requestRenderAll();
  }
  function overlayGesture(event) {
    fastOn(event.target);
    if (event.target === overlay) { syncOverlayTransform(); }
  }
  canvas.on('object:moving', overlayGesture);
  canvas.on('object:scaling', overlayGesture);
  canvas.on('object:rotating', overlayGesture);
  canvas.on('object:modified', function (event) {
    fastOff();
    if (event.target === overlay) { syncOverlayTransform(); }
    raiseOverlay();
  });
  canvas.on('mouse:up', fastOff);
  canvas.on('selection:created', syncPanel);
  canvas.on('selection:updated', syncPanel);
  canvas.on('selection:cleared', syncPanel);

  $('overlayKind').addEventListener('change', function (event) {
    var kind = event.target.value;
    if (kind === 'none') {
      removeOverlay();
    } else {
      applyOverlay(kind, 1);
    }
  });
  $('overlayPlace').addEventListener('click', function () {
    if (!overlay) { return; }
    setOverlayEditing(!overlayEditing);
  });
  $('overlayX').addEventListener('input', function (event) {
    if (!overlay) { return; }
    var transform = overlayTransform();
    transform.x = Number(event.target.value);
    setOverlayTransform(transform);
  });
  $('overlayY').addEventListener('input', function (event) {
    if (!overlay) { return; }
    var transform = overlayTransform();
    transform.y = Number(event.target.value);
    setOverlayTransform(transform);
  });
  $('overlayScale').addEventListener('input', function (event) {
    if (!overlay) { return; }
    var transform = overlayTransform();
    transform.scale = Number(event.target.value);
    setOverlayTransform(transform);
  });
  $('overlayRotate').addEventListener('input', function (event) {
    if (!overlay) { return; }
    var transform = overlayTransform();
    transform.angle = Number(event.target.value);
    setOverlayTransform(transform);
  });
  $('overlayReset').addEventListener('click', function () {
    if (!overlay) { return; }
    setOverlayTransform({ x: 0, y: 0, scale: 100, angle: 0 });
  });
  $('overlayOpacity').addEventListener('input', function (event) {
    if (!overlay) { return; }
    overlay.set('opacity', Number(event.target.value) / 100);
    $('overlayValue').textContent = event.target.value;
    canvas.requestRenderAll();
  });
  $('overlayBlend').addEventListener('change', function (event) {
    if (!overlay) { return; }
    overlay.set('globalCompositeOperation', event.target.value);
    canvas.requestRenderAll();
  });
  $('overlayShuffle').addEventListener('click', function () {
    if (!overlay) { return; }
    overlaySeed += 1;
    applyOverlay(overlayKind, overlaySeed);
  });
  $('overlayRemove').addEventListener('click', removeOverlay);

  $('photo').addEventListener('click', function () {
    if (window.StudioApp && StudioApp.pickPhoto) { StudioApp.pickPhoto(); }
  });
  $('export').addEventListener('click', function () {
    var data = canvas.toDataURL({ format: 'png', multiplier: 2, quality: 1 });
    if (window.StudioApp && StudioApp.savePng) {
      StudioApp.savePng(data);
      flash('Exporting at 2\u00d7');
    }
  });
  $('back').addEventListener('click', function () {
    if (window.StudioApp && StudioApp.close) { StudioApp.close(); }
  });

  window.studioSetPhoto = function (dataUrl) {
    fabric.Image.fromURL(dataUrl, function (image) {
      var scale = Math.max(W / image.width, H / image.height);
      image.set({
        left: (W - image.width * scale) / 2,
        top: (H - image.height * scale) / 2,
        scaleX: scale,
        scaleY: scale,
        originX: 'left',
        originY: 'top',
        selectable: false,
        evented: false
      });
      canvas.setBackgroundImage(image, canvas.renderAll.bind(canvas));
      flash('Photo added');
    });
  };

  // The interaction layer (Draggable, snapping, Observer, MotionPath) runs after
  // this module without a second canvas. Expose the one engine and its helpers.
  window.StudioEngine = {
    canvas: canvas,
    W: W,
    H: H,
    activeText: activeText,
    addText: addText,
    syncPanel: syncPanel,
    fitCanvas: fitCanvas,
    flash: flash,
    getOverlay: function () { return overlay; }
  };

  syncOverlayPanel();
  fitCanvas();
  if (window.requestAnimationFrame) { requestAnimationFrame(fitCanvas); }
  if (window.ResizeObserver) {
    new ResizeObserver(fitCanvas).observe(stageEl);
  } else {
    window.addEventListener('resize', fitCanvas);
  }

  addText('ART', { left: 50, top: 150, width: 440, fontSize: 154, fontWeight: '600' }, false);
  addText('DESIGN', {
    left: 50, top: 355, width: 440, fontSize: 96, fontWeight: '500', gradientAngle: 0
  }, false);
  canvas.requestRenderAll();
  syncPanel();
})();
