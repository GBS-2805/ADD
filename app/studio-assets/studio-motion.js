/* ADD Studio motion: bundled GSAP, no network, reduced-motion aware. */
(function () {
  'use strict';

  if (!window.gsap) { return; }

  var reduce = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var root = document.documentElement;
  var version = window.gsap.version;
  root.dataset.motion = 'gsap-' + version;

  function init() {
    var top = document.querySelector('.chrome-top');
    var doc = document.querySelector('.document-chip');
    var pill = document.querySelector('.tool-pill');
    var selection = document.getElementById('selection');
    var overlayGrid = document.getElementById('overlayTransform');

    if (!reduce) {
      var intro = window.gsap.timeline({ defaults: { ease: 'power3.out' } });
      if (top) {
        intro.from(top, {
          y: -8, opacity: 0, duration: 0.32,
          clearProps: 'transform,opacity'
        });
      }
      if (doc) {
        intro.from(doc, {
          opacity: 0, x: -4, duration: 0.3,
          clearProps: 'transform,opacity'
        }, '-=0.18');
      }
      if (pill) {
        intro.from(pill, {
          y: 12, opacity: 0, duration: 0.38,
          clearProps: 'transform,opacity'
        }, '-=0.24');
      }
    }

    // Fabric owns selection; observe the label rather than adding a second source
    // of truth. Every new layer gets a short, readable confirmation pulse.
    if (selection && window.MutationObserver) {
      var lastSelection = selection.textContent;
      new window.MutationObserver(function () {
        if (selection.textContent === lastSelection) { return; }
        lastSelection = selection.textContent;
        if (reduce) { return; }
        window.gsap.fromTo(selection,
          { x: -4, opacity: 0.55 },
          { x: 0, opacity: 1, duration: 0.32, ease: 'power2.out', overwrite: true });
      }).observe(selection, { childList: true, characterData: true, subtree: true });
    }

    // X/Y/scale/rotate is created by overlays.js only after Place. The grid is
    // display:grid, so animating from height:0 gives the inspector a real reveal
    // without changing its scroll position or Fabric's canvas geometry.
    if (overlayGrid && window.MutationObserver) {
      var lastHidden = overlayGrid.hidden;
      new window.MutationObserver(function () {
        if (overlayGrid.hidden === lastHidden) { return; }
        lastHidden = overlayGrid.hidden;
        if (reduce || overlayGrid.hidden) { return; }
        window.gsap.from(overlayGrid, {
          height: 0, opacity: 0, y: -6, duration: 0.3, ease: 'power2.out',
          clearProps: 'height,opacity,transform'
        });
      }).observe(overlayGrid, { attributes: true, attributeFilter: ['hidden'] });
    }

    // The review harness reads this; it is also a cheap support signal when a
    // WebView is stripped of its bundled scripts.
    window.__studioMotion = {
      engine: 'gsap',
      version: version,
      plugins: ['core'],
      reduced: !!reduce,
      duration: reduce ? 0 : 0.38,
      shell: true
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
