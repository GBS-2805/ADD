/*
 * Small dependency-free PNG comparator for the Studio visual guard.
 *
 * Chromium screenshots are 8-bit RGB/RGBA, non-interlaced PNGs. Keeping the
 * decoder here avoids adding a package-manager dependency just to catch layout
 * and colour regressions. It intentionally rejects unsupported PNG formats.
 */
'use strict';

const fs = require('fs');
const zlib = require('zlib');

const SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
let crcTable = null;

function crc32(buffer) {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n += 1) {
      let value = n;
      for (let bit = 0; bit < 8; bit += 1) {
        value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
      }
      crcTable[n] = value >>> 0;
    }
  }
  let value = 0xffffffff;
  for (let i = 0; i < buffer.length; i += 1) {
    value = crcTable[(value ^ buffer[i]) & 0xff] ^ (value >>> 8);
  }
  return (value ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const name = Buffer.from(type, 'ascii');
  const body = Buffer.concat([name, data]);
  const header = Buffer.alloc(4);
  header.writeUInt32BE(data.length, 0);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([header, body, checksum]);
}

function paeth(left, up, upLeft) {
  const estimate = left + up - upLeft;
  const leftDistance = Math.abs(estimate - left);
  const upDistance = Math.abs(estimate - up);
  const upLeftDistance = Math.abs(estimate - upLeft);
  if (leftDistance <= upDistance && leftDistance <= upLeftDistance) { return left; }
  return upDistance <= upLeftDistance ? up : upLeft;
}

function decodePng(buffer) {
  if (buffer.length < 8 || !buffer.subarray(0, 8).equals(SIGNATURE)) {
    throw new Error('not a PNG');
  }
  let width = 0;
  let height = 0;
  let bitDepth = 0;
  let colorType = 0;
  let interlace = 0;
  const idat = [];
  let offset = 8;
  while (offset + 12 <= buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') {
      break;
    }
    offset += 12 + length;
  }
  if (!width || !height || bitDepth !== 8 || interlace !== 0) {
    throw new Error(`unsupported PNG: ${width}x${height}, bitDepth=${bitDepth}, interlace=${interlace}`);
  }
  if (colorType !== 2 && colorType !== 6) {
    throw new Error(`unsupported PNG color type ${colorType}`);
  }
  const bpp = colorType === 6 ? 4 : 3;
  const stride = width * bpp;
  const inflated = zlib.inflateSync(Buffer.concat(idat));
  const pixels = Buffer.alloc(width * height * 4);
  let previous = Buffer.alloc(stride);
  let source = 0;
  for (let y = 0; y < height; y += 1) {
    const filter = inflated[source];
    source += 1;
    const row = Buffer.alloc(stride);
    for (let x = 0; x < stride; x += 1) {
      const left = x >= bpp ? row[x - bpp] : 0;
      const up = previous[x];
      const upLeft = x >= bpp ? previous[x - bpp] : 0;
      const raw = inflated[source];
      source += 1;
      let value;
      if (filter === 0) { value = raw; }
      else if (filter === 1) { value = raw + left; }
      else if (filter === 2) { value = raw + up; }
      else if (filter === 3) { value = raw + Math.floor((left + up) / 2); }
      else if (filter === 4) { value = raw + paeth(left, up, upLeft); }
      else { throw new Error(`unsupported PNG filter ${filter}`); }
      row[x] = value & 0xff;
    }
    for (let x = 0; x < width; x += 1) {
      const target = (y * width + x) * 4;
      const input = x * bpp;
      pixels[target] = row[input];
      pixels[target + 1] = row[input + 1];
      pixels[target + 2] = row[input + 2];
      pixels[target + 3] = colorType === 6 ? row[input + 3] : 255;
    }
    previous = row;
  }
  return { width, height, pixels };
}

function encodePng(width, height, pixels) {
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y += 1) {
    raw[y * (stride + 1)] = 0;
    pixels.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  header[10] = 0;
  header[11] = 0;
  header[12] = 0;
  return Buffer.concat([
    SIGNATURE,
    chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function comparePng(actualPath, baselinePath, options = {}) {
  const threshold = options.threshold == null ? 8 : options.threshold;
  const actual = decodePng(fs.readFileSync(actualPath));
  const baseline = decodePng(fs.readFileSync(baselinePath));
  if (actual.width !== baseline.width || actual.height !== baseline.height) {
    return {
      pass: false,
      width: actual.width,
      height: actual.height,
      baselineWidth: baseline.width,
      baselineHeight: baseline.height,
      diffPixels: actual.width * actual.height,
      totalPixels: actual.width * actual.height,
      ratio: 1,
      diff: null
    };
  }
  const totalPixels = actual.width * actual.height;
  const diff = Buffer.alloc(totalPixels * 4);
  let diffPixels = 0;
  let maxChannelDelta = 0;
  for (let i = 0; i < actual.pixels.length; i += 4) {
    const delta = Math.max(
      Math.abs(actual.pixels[i] - baseline.pixels[i]),
      Math.abs(actual.pixels[i + 1] - baseline.pixels[i + 1]),
      Math.abs(actual.pixels[i + 2] - baseline.pixels[i + 2]),
      Math.abs(actual.pixels[i + 3] - baseline.pixels[i + 3])
    );
    if (delta > maxChannelDelta) { maxChannelDelta = delta; }
    if (delta > threshold) {
      diffPixels += 1;
      diff[i] = 255;
      diff[i + 1] = 28;
      diff[i + 2] = 28;
      diff[i + 3] = 255;
    } else {
      // Dim the unchanged image so changed pixels are obvious in the report.
      diff[i] = Math.round(actual.pixels[i] * 0.25);
      diff[i + 1] = Math.round(actual.pixels[i + 1] * 0.25);
      diff[i + 2] = Math.round(actual.pixels[i + 2] * 0.25);
      diff[i + 3] = 255;
    }
  }
  return {
    pass: diffPixels === 0,
    width: actual.width,
    height: actual.height,
    totalPixels,
    diffPixels,
    ratio: diffPixels / totalPixels,
    maxChannelDelta,
    diff: encodePng(actual.width, actual.height, diff)
  };
}

module.exports = { comparePng, decodePng, encodePng };
