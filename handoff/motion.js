/* Handoff motion: locally bundled GSAP core + Flip + ScrollTrigger. */
(function () {
  'use strict';
  if (!window.gsap) { return; }

  var reduce = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var gsap = window.gsap;
  var version = gsap.version;
  if (window.Flip && window.ScrollTrigger) {
    gsap.registerPlugin(window.Flip, window.ScrollTrigger);
  }
  document.documentElement.dataset.motion = 'gsap-' + version;

  var shots = Array.prototype.slice.call(document.querySelectorAll('.gsap-shot'));
  var filters = Array.prototype.slice.call(document.querySelectorAll('.gsap-filter'));
  var dialog = document.getElementById('engine-lightbox');
  var dialogImage = document.getElementById('engine-lightbox-image');
  var dialogCaption = document.getElementById('engine-lightbox-caption');
  var dialogClose = document.getElementById('engine-lightbox-close');

  if (!reduce && window.ScrollTrigger) {
    // Cards enter only as they reach the thumb; the images are visible by default,
    // so a stripped WebView still shows every frame if GSAP does not run.
    window.ScrollTrigger.batch(shots, {
      start: 'top 94%',
      once: true,
      onEnter: function (batch) {
        gsap.from(batch, {
          opacity: 0, y: 20, duration: 0.55, stagger: 0.07,
          ease: 'power3.out', clearProps: 'transform,opacity'
        });
      }
    });
  }

  function setFilter(kind) {
    var visible = shots.filter(function (shot) { return !shot.hidden; });
    var state = window.Flip ? window.Flip.getState(visible, { props: 'opacity' }) : null;

    shots.forEach(function (shot) {
      shot.hidden = kind !== 'all' && shot.dataset.kind !== kind;
    });
    filters.forEach(function (button) {
      button.setAttribute('aria-pressed', button.dataset.filter === kind ? 'true' : 'false');
    });

    var next = shots.filter(function (shot) { return !shot.hidden; });
    if (!reduce && state && window.Flip) {
      window.Flip.from(state, {
        duration: 0.45,
        ease: 'power3.out',
        absolute: true,
        stagger: 0.02,
        onComplete: function () { window.ScrollTrigger.refresh(); }
      });
      gsap.from(next, {
        opacity: 0, y: 12, duration: 0.35, stagger: 0.04,
        ease: 'power2.out', clearProps: 'transform,opacity'
      });
    } else if (window.ScrollTrigger) {
      window.ScrollTrigger.refresh();
    }
  }

  filters.forEach(function (button) {
    button.addEventListener('click', function () {
      setFilter(button.dataset.filter || 'all');
    });
  });

  function openShot(figure) {
    if (!dialog || !dialogImage) { return; }
    var image = figure.querySelector('img');
    if (!image) { return; }
    dialogImage.src = image.getAttribute('src');
    dialogImage.alt = image.alt || 'Engine frame preview';
    dialogCaption.textContent = figure.querySelector('figcaption') ?
      figure.querySelector('figcaption').textContent : '';
    dialog.showModal();
    if (!reduce) {
      gsap.fromTo(dialog, { opacity: 0 }, { opacity: 1, duration: 0.2, ease: 'power1.out' });
      gsap.fromTo(dialogImage,
        { scale: 0.94, opacity: 0 },
        { scale: 1, opacity: 1, duration: 0.35, ease: 'power3.out', clearProps: 'transform,opacity' });
    }
  }

  shots.forEach(function (figure) {
    var link = figure.querySelector('a');
    if (!link) { return; }
    link.addEventListener('click', function (event) {
      if (event.metaKey || event.ctrlKey || event.shiftKey) { return; }
      event.preventDefault();
      openShot(figure);
    });
  });

  if (dialogClose && dialog) {
    dialogClose.addEventListener('click', function () { dialog.close(); });
  }
  if (dialog) {
    dialog.addEventListener('click', function (event) {
      if (event.target === dialog) { dialog.close(); }
    });
  }

  window.__handoffMotion = {
    engine: 'gsap',
    version: version,
    plugins: ['Flip', 'ScrollTrigger'],
    reduced: !!reduce,
    shots: shots.length
  };
})();
