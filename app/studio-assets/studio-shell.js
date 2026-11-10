/*
 * ADD Studio shell: canvas-first chrome over the existing Fabric engine.
 * Move leaves the stage alone; the other tools open the one peek inspector and
 * expose the controls that were already wired in studio.js. No tool is removed.
 */
(function () {
  'use strict';

  var body = document.body;
  var peek = document.getElementById('peek');
  var peekTitle = document.getElementById('peekTitle');
  var peekClose = document.getElementById('peekClose');
  var peekSelection = document.getElementById('peekSelection');
  var selection = document.getElementById('selection');
  var toolButtons = Array.prototype.slice.call(document.querySelectorAll('[data-tool]'));
  var sheets = Array.prototype.slice.call(document.querySelectorAll('[data-sheet]'));
  var names = { type: 'TYPE', style: 'STYLE', texture: 'TEXTURE', motion: 'MOTION' };
  var reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  var active = 'move';

  function setPressed(name) {
    toolButtons.forEach(function (button) {
      var on = button.getAttribute('data-tool') === name;
      button.classList.toggle('is-active', on);
      button.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
  }

  function setSheets(name) {
    sheets.forEach(function (sheet) {
      sheet.hidden = sheet.getAttribute('data-sheet') !== name;
    });
  }

  function showPeek(name) {
    if (!peek) { return; }
    peek.hidden = false;
    body.classList.add('peek-open');
    setSheets(name);
    if (peekTitle) { peekTitle.textContent = names[name] || 'TYPE'; }
    if (window.gsap && !reduce) {
      window.gsap.killTweensOf(peek);
      window.gsap.fromTo(peek,
        { y: 16, opacity: 0 },
        { y: 0, opacity: 1, duration: 0.22, ease: 'power2.out', clearProps: 'transform,opacity' });
    }
  }

  function hidePeek() {
    if (!peek || peek.hidden) { return; }
    body.classList.remove('peek-open');
    if (window.gsap && !reduce) {
      window.gsap.killTweensOf(peek);
      window.gsap.to(peek, {
        y: 14,
        opacity: 0,
        duration: 0.16,
        ease: 'power2.in',
        onComplete: function () {
          peek.hidden = true;
          window.gsap.set(peek, { clearProps: 'transform,opacity' });
        }
      });
    } else {
      peek.hidden = true;
    }
  }

  function setTool(name) {
    active = name;
    body.setAttribute('data-tool', name);
    setPressed(name);
    if (name === 'move') {
      hidePeek();
    } else {
      showPeek(name);
    }
  }

  toolButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      setTool(button.getAttribute('data-tool'));
    });
  });

  if (peekClose) {
    peekClose.addEventListener('click', function () { setTool('move'); });
  }

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && active !== 'move') {
      setTool('move');
    }
  });

  // Chrome yields to the artwork: a canvas touch closes the inspector but leaves
  // the selected layer and its live frame intact.
  if (window.StudioEngine && window.StudioEngine.canvas) {
    window.StudioEngine.canvas.on('mouse:down', function () {
      if (active !== 'move') { setTool('move'); }
    });
  }

  if (selection && peekSelection && window.MutationObserver) {
    var copy = function () {
      peekSelection.textContent = selection.textContent || 'Nothing selected';
    };
    copy();
    new window.MutationObserver(copy).observe(selection, {
      childList: true, characterData: true, subtree: true
    });
  }

  window.__studioShell = {
    active: function () { return active; },
    open: function (name) { setTool(name); },
    close: function () { setTool('move'); }
  };

  setPressed('move');
})();
