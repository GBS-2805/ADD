/*
 * Procedural overlay textures for the Studio. Everything is drawn locally, so the
 * app stays offline and no stock asset or licence is involved. The families mirror
 * the useful PSD overlay shelf: light leak, grain, bokeh, paper, dust, vignette,
 * window shadow and lens flare.
 */
(function () {
  'use strict';

  function random(seed) {
    var t = (seed || 1) >>> 0;
    return function () {
      t += 0x6D2B79F5;
      var r = Math.imul(t ^ (t >>> 15), 1 | t);
      r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }

  function surface(w, h) {
    var canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    return canvas;
  }

  function noise(ctx, w, h, rand, strength) {
    var image = ctx.createImageData(w, h);
    var data = image.data;
    for (var i = 0; i < data.length; i += 4) {
      var value = 96 + Math.floor(rand() * 150);
      data[i] = value;
      data[i + 1] = value;
      data[i + 2] = value;
      data[i + 3] = Math.floor(rand() * strength);
    }
    ctx.putImageData(image, 0, 0);
  }

  function specks(ctx, w, h, rand, count, light) {
    for (var i = 0; i < count; i++) {
      var r = rand() * 2.1 + 0.4;
      var x = rand() * w;
      var y = rand() * h;
      var alpha = 0.12 + rand() * 0.5;
      ctx.fillStyle = (light && rand() > 0.45) ? 'rgba(255,250,235,' + alpha + ')'
        : 'rgba(12,10,8,' + alpha + ')';
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function scratches(ctx, w, h, rand, count) {
    for (var i = 0; i < count; i++) {
      var x = rand() * w;
      var y = rand() * h;
      var len = 20 + rand() * h * 0.65;
      var bend = (rand() - 0.5) * 26;
      ctx.strokeStyle = 'rgba(255,248,232,' + (0.04 + rand() * 0.16) + ')';
      ctx.lineWidth = 0.35 + rand() * 1.1;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.quadraticCurveTo(x + bend, y + len * 0.5, x + bend * 0.4, y + len);
      ctx.stroke();
    }
  }

  function light(ctx, w, h, rand) {
    ctx.globalCompositeOperation = 'lighter';
    for (var i = 0; i < 7; i++) {
      var x = w * (0.1 + rand() * 0.9);
      var y = h * (0.05 + rand() * 0.9);
      var radius = Math.max(w, h) * (0.12 + rand() * 0.35);
      var warm = rand() > 0.35;
      var gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
      gradient.addColorStop(0, warm ? 'rgba(255,196,118,0.52)' : 'rgba(255,105,138,0.36)');
      gradient.addColorStop(0.45, warm ? 'rgba(255,126,54,0.18)' : 'rgba(225,70,150,0.13)');
      gradient.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, w, h);
    }
    var band = ctx.createLinearGradient(0, h, w, 0);
    band.addColorStop(0, 'rgba(255,120,40,0)');
    band.addColorStop(0.45, 'rgba(255,180,90,0.18)');
    band.addColorStop(0.55, 'rgba(255,220,150,0.28)');
    band.addColorStop(1, 'rgba(255,90,40,0)');
    ctx.fillStyle = band;
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
  }

  function grain(ctx, w, h, rand) {
    noise(ctx, w, h, rand, 78);
    specks(ctx, w, h, rand, 90, true);
    scratches(ctx, w, h, rand, 7);
  }

  function bokeh(ctx, w, h, rand) {
    ctx.globalCompositeOperation = 'lighter';
    var palette = ['255,214,150', '255,166,120', '255,120,150', '144,220,255', '210,180,255'];
    for (var i = 0; i < 34; i++) {
      var radius = 5 + rand() * 38;
      var x = rand() * w;
      var y = rand() * h;
      var colour = palette[Math.floor(rand() * palette.length)];
      var gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
      gradient.addColorStop(0, 'rgba(' + colour + ',0.32)');
      gradient.addColorStop(0.68, 'rgba(' + colour + ',0.14)');
      gradient.addColorStop(1, 'rgba(' + colour + ',0)');
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalCompositeOperation = 'source-over';
  }

  function paper(ctx, w, h, rand) {
    ctx.fillStyle = 'rgba(239,231,211,0.42)';
    ctx.fillRect(0, 0, w, h);
    for (var i = 0; i < 420; i++) {
      var x = rand() * w;
      var y = rand() * h;
      var len = 2 + rand() * 16;
      ctx.strokeStyle = rand() > 0.5 ? 'rgba(120,104,82,0.08)' : 'rgba(255,252,238,0.12)';
      ctx.lineWidth = 0.5;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x + len, y + (rand() - 0.5) * 2);
      ctx.stroke();
    }
    var crease = ctx.createLinearGradient(0, 0, w, h);
    crease.addColorStop(0, 'rgba(80,66,48,0)');
    crease.addColorStop(0.48, 'rgba(80,66,48,0.06)');
    crease.addColorStop(0.52, 'rgba(80,66,48,0.12)');
    crease.addColorStop(1, 'rgba(80,66,48,0)');
    ctx.fillStyle = crease;
    ctx.fillRect(0, 0, w, h);
    noise(ctx, w, h, rand, 22);
  }

  function dust(ctx, w, h, rand) {
    specks(ctx, w, h, rand, 230, true);
    scratches(ctx, w, h, rand, 22);
    for (var i = 0; i < 8; i++) {
      var x = rand() * w;
      var y = rand() * h;
      ctx.strokeStyle = 'rgba(24,20,16,' + (0.08 + rand() * 0.18) + ')';
      ctx.lineWidth = 0.6 + rand() * 1.2;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.bezierCurveTo(x + 20, y - 8, x + 34, y + 12, x + 58, y + 2);
      ctx.stroke();
    }
  }

  function vignette(ctx, w, h) {
    var gradient = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.22,
      w / 2, h / 2, Math.max(w, h) * 0.72);
    gradient.addColorStop(0, 'rgba(0,0,0,0)');
    gradient.addColorStop(0.62, 'rgba(0,0,0,0.08)');
    gradient.addColorStop(1, 'rgba(0,0,0,0.72)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, w, h);
  }

  function shadows(ctx, w, h, rand) {
    ctx.save();
    ctx.translate(w / 2, h / 2);
    ctx.rotate(-0.38);
    for (var i = -2; i <= 3; i++) {
      var x = i * w * 0.22 + (rand() - 0.5) * 20;
      var band = ctx.createLinearGradient(x, 0, x + w * 0.16, 0);
      band.addColorStop(0, 'rgba(0,0,0,0)');
      band.addColorStop(0.35, 'rgba(0,0,0,0.16)');
      band.addColorStop(0.7, 'rgba(0,0,0,0.1)');
      band.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = band;
      ctx.fillRect(x, -h, w * 0.16, h * 2);
    }
    ctx.restore();
    specks(ctx, w, h, rand, 30, false);
  }

  function flare(ctx, w, h, rand) {
    ctx.globalCompositeOperation = 'lighter';
    var streak = ctx.createLinearGradient(0, h * 0.42, w, h * 0.58);
    streak.addColorStop(0, 'rgba(255,210,150,0)');
    streak.addColorStop(0.35, 'rgba(255,230,180,0.34)');
    streak.addColorStop(0.5, 'rgba(255,255,245,0.72)');
    streak.addColorStop(0.62, 'rgba(255,214,160,0.28)');
    streak.addColorStop(1, 'rgba(255,190,120,0)');
    ctx.fillStyle = streak;
    ctx.fillRect(0, h * 0.4, w, h * 0.2);
    for (var i = 0; i < 6; i++) {
      var x = rand() * w;
      var y = rand() * h;
      var radius = 3 + rand() * 16;
      var g = ctx.createRadialGradient(x, y, 0, x, y, radius);
      g.addColorStop(0, 'rgba(255,248,225,0.55)');
      g.addColorStop(1, 'rgba(255,190,120,0)');
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalCompositeOperation = 'source-over';
  }

  /* The five press materials are the brand bridge: each is drawn locally, with
   * no stock asset, and lands in the same overlay slot as the atmosphere set. */
  function newsprint(ctx, w, h, rand) {
    ctx.fillStyle = 'rgba(233,224,203,0.42)';
    ctx.fillRect(0, 0, w, h);
    for (var i = 0; i < 760; i++) {
      var x = rand() * w;
      var y = rand() * h;
      var len = 3 + rand() * 22;
      ctx.strokeStyle = rand() > 0.46 ? 'rgba(92,78,58,0.1)' : 'rgba(255,252,239,0.14)';
      ctx.lineWidth = 0.45;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x + len, y + (rand() - 0.5) * 2.4);
      ctx.stroke();
    }
    for (var j = 0; j < 34; j++) {
      ctx.fillStyle = 'rgba(54,45,34,' + (0.02 + rand() * 0.05) + ')';
      ctx.fillRect(rand() * w, rand() * h, 1 + rand() * 2, 1 + rand() * 2);
    }
    noise(ctx, w, h, rand, 28);
  }

  function wheatpaste(ctx, w, h, rand) {
    for (var i = 0; i < 22; i++) {
      var x = rand() * w;
      var y = rand() * h;
      var radius = 20 + rand() * Math.max(w, h) * 0.24;
      var blob = ctx.createRadialGradient(x, y, 0, x, y, radius);
      blob.addColorStop(0, 'rgba(238,226,203,' + (0.08 + rand() * 0.12) + ')');
      blob.addColorStop(0.62, 'rgba(199,180,151,' + (0.04 + rand() * 0.08) + ')');
      blob.addColorStop(1, 'rgba(80,68,51,0)');
      ctx.fillStyle = blob;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fill();
    }
    for (var j = 0; j < 18; j++) {
      var sx = rand() * w;
      var sy = rand() * h;
      var length = 12 + rand() * 120;
      ctx.strokeStyle = 'rgba(249,240,220,' + (0.04 + rand() * 0.12) + ')';
      ctx.lineWidth = 0.6 + rand() * 1.8;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.quadraticCurveTo(sx + length * 0.4, sy + (rand() - 0.5) * 22,
        sx + length, sy + (rand() - 0.5) * 38);
      ctx.stroke();
    }
    noise(ctx, w, h, rand, 34);
  }

  function overexposed(ctx, w, h, rand) {
    ctx.globalCompositeOperation = 'lighter';
    for (var i = 0; i < 5; i++) {
      var x = w * (0.05 + rand() * 0.9);
      var y = h * (0.05 + rand() * 0.9);
      var radius = Math.max(w, h) * (0.2 + rand() * 0.4);
      var glow = ctx.createRadialGradient(x, y, 0, x, y, radius);
      glow.addColorStop(0, 'rgba(255,252,239,0.75)');
      glow.addColorStop(0.25, 'rgba(255,241,210,0.35)');
      glow.addColorStop(0.7, 'rgba(255,220,170,0.08)');
      glow.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = glow;
      ctx.fillRect(0, 0, w, h);
    }
    ctx.globalCompositeOperation = 'source-over';
    specks(ctx, w, h, rand, 28, false);
    noise(ctx, w, h, rand, 18);
  }

  function halftone(ctx, w, h, rand) {
    var gap = 7;
    var radius = 2.2;
    for (var y = 0; y < h; y += gap) {
      for (var x = 0; x < w; x += gap) {
        var wave = (Math.sin((x + y * 0.35) * 0.025) + 1) / 2;
        var r = radius * (0.45 + wave * 0.9);
        ctx.fillStyle = 'rgba(21,18,14,' + (0.16 + wave * 0.2) + ')';
        ctx.beginPath();
        ctx.arc(x + (y % (gap * 2) ? gap / 2 : 0), y, r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    noise(ctx, w, h, rand, 14);
  }

  function rough(ctx, w, h, rand) {
    ctx.fillStyle = 'rgba(128,113,92,0.1)';
    ctx.fillRect(0, 0, w, h);
    for (var y = -h; y < h * 2; y += 4) {
      ctx.strokeStyle = 'rgba(242,238,227,' + (0.03 + rand() * 0.06) + ')';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(0, y + rand() * 3);
      ctx.lineTo(w, y + h * 0.36 + rand() * 3);
      ctx.stroke();
    }
    specks(ctx, w, h, rand, 140, true);
    noise(ctx, w, h, rand, 32);
  }

  var builders = {
    newsprint: newsprint,
    wheatpaste: wheatpaste,
    overexposed: overexposed,
    halftone: halftone,
    rough: rough,
    light: light,
    grain: grain,
    bokeh: bokeh,
    paper: paper,
    dust: dust,
    vignette: vignette,
    shadows: shadows,
    flare: flare
  };

  window.overlayDefaults = {
    newsprint: { blend: 'multiply', opacity: 0.34 },
    wheatpaste: { blend: 'overlay', opacity: 0.4 },
    overexposed: { blend: 'screen', opacity: 0.5 },
    halftone: { blend: 'multiply', opacity: 0.34 },
    rough: { blend: 'multiply', opacity: 0.4 },
    light: { blend: 'screen', opacity: 0.55 },
    grain: { blend: 'overlay', opacity: 0.42 },
    bokeh: { blend: 'screen', opacity: 0.45 },
    paper: { blend: 'multiply', opacity: 0.38 },
    dust: { blend: 'screen', opacity: 0.38 },
    vignette: { blend: 'multiply', opacity: 0.5 },
    shadows: { blend: 'multiply', opacity: 0.52 },
    flare: { blend: 'screen', opacity: 0.4 }
  };

  window.makeOverlay = function (kind, seed, w, h) {
    var build = builders[kind];
    if (!build) {
      return null;
    }
    var canvas = surface(w, h);
    var ctx = canvas.getContext('2d');
    build(ctx, w, h, random(seed));
    return canvas.toDataURL('image/png');
  };
})();
