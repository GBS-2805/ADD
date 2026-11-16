import { PROJECT_EXTENSION, projectFilename } from "./project";
import { SHADER_COLORS } from "./presets";
import {
  ARTBOARD_EXPORT_HEIGHT as GEOMETRY_EXPORT_HEIGHT,
  ARTBOARD_EXPORT_WIDTH as GEOMETRY_EXPORT_WIDTH,
  ARTBOARD_HEIGHT,
  ARTBOARD_WIDTH,
  EXPORT_SCALE,
  baseLayerSize,
} from "../editor/model/geometry";
import {
  textFontStack,
  type BlendMode,
  type ShapeVariant,
  type TwinDocument,
  type TwinEffect,
  type TwinLayer,
} from "./types";

export const PNG_MIME = "image/png";
export const PNG_SIGNATURE = "89504e470d0a1a0a";
export const ARTBOARD_DESIGN_WIDTH = ARTBOARD_WIDTH;
export const ARTBOARD_DESIGN_HEIGHT = ARTBOARD_HEIGHT;
export const ARTBOARD_EXPORT_WIDTH = GEOMETRY_EXPORT_WIDTH;
export const ARTBOARD_EXPORT_HEIGHT = GEOMETRY_EXPORT_HEIGHT;
export const ARTBOARD_PIXEL_SCALE = EXPORT_SCALE;

const PAPER = "#f2f1ed";
const SHEET = "#fafaf7";
const INK = "#111111";

interface Point {
  x: number;
  y: number;
}

interface Rect extends Point {
  width: number;
  height: number;
}

export interface ArtboardRenderPlan {
  width: number;
  height: number;
  designWidth: number;
  designHeight: number;
  scale: number;
  filename: string;
  background: TwinEffect | null;
  layers: TwinLayer[];
}

export interface PngInfo {
  width: number;
  height: number;
  signature: typeof PNG_SIGNATURE;
}

export interface PngExportResult extends PngInfo {
  blob: Blob;
  bytes: number;
  filename: string;
}

export interface PngExportDependencies {
  createCanvas?: (width: number, height: number) => HTMLCanvasElement;
  loadImage?: (src: string) => Promise<CanvasImageSource | null>;
}

type LoadedImages = Map<string, CanvasImageSource | null>;

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

const controlNumber = (effect: TwinEffect, key: string, fallback: number): number => {
  if (!("controls" in effect)) return fallback;
  const value = Number(effect.controls[key]);
  return Number.isFinite(value) ? value : fallback;
};

const controlColour = (effect: TwinEffect, key: string, fallback: string): string => {
  if (!("controls" in effect)) return fallback;
  const value = effect.controls[key];
  return typeof value === "string" && value ? value : fallback;
};

export function blendOperation(mode: BlendMode): GlobalCompositeOperation {
  return mode === "normal" ? "source-over" : (mode as GlobalCompositeOperation);
}

export function buildArtboardRenderPlan(doc: TwinDocument): ArtboardRenderPlan {
  return {
    width: ARTBOARD_EXPORT_WIDTH,
    height: ARTBOARD_EXPORT_HEIGHT,
    designWidth: ARTBOARD_DESIGN_WIDTH,
    designHeight: ARTBOARD_DESIGN_HEIGHT,
    scale: ARTBOARD_PIXEL_SCALE,
    filename: `${projectFilename(doc).slice(0, -PROJECT_EXTENSION.length)}.png`,
    background: doc.background,
    layers: doc.layers.filter((layer) => layer.visible),
  };
}

export function readPngInfo(bytes: Uint8Array): PngInfo | null {
  if (bytes.length < 24) return null;
  const signature = [...bytes.slice(0, 8)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  if (signature !== PNG_SIGNATURE) return null;
  if (String.fromCharCode(...bytes.slice(12, 16)) !== "IHDR") return null;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const width = view.getUint32(16);
  const height = view.getUint32(20);
  if (width === 0 || height === 0) return null;
  return { width, height, signature: PNG_SIGNATURE };
}

function makeRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 0x1_0000_0000;
  };
}

function rgba(colour: string, alpha: number): string {
  const match = /^#([0-9a-f]{6})(?:[0-9a-f]{2})?$/i.exec(colour);
  if (!match) return colour;
  const value = Number.parseInt(match[1], 16);
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`;
}

function shaderPalette(colour: string | undefined): string[] {
  const selected = colour && SHADER_COLORS.includes(colour) ? colour : SHADER_COLORS[0];
  return [selected, ...SHADER_COLORS.filter((value) => value !== selected)];
}

function line(ctx: CanvasRenderingContext2D, from: Point, to: Point): void {
  ctx.moveTo(from.x, from.y);
  ctx.lineTo(to.x, to.y);
}

function fillBase(ctx: CanvasRenderingContext2D, rect: Rect, colour: string): void {
  ctx.fillStyle = colour;
  ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
}

function paintPaperShader(ctx: CanvasRenderingContext2D, effect: TwinEffect, rect: Rect): void {
  const grain = clamp(controlNumber(effect, "grain", 0.2), 0, 1);
  const roughness = clamp(controlNumber(effect, "roughness", grain), 0, 1);
  const paper = controlColour(effect, "paper", PAPER);
  fillBase(ctx, rect, paper);

  const light = ctx.createRadialGradient(
    rect.x + rect.width * 0.42,
    rect.y + rect.height * 0.3,
    0,
    rect.x + rect.width * 0.42,
    rect.y + rect.height * 0.3,
    Math.max(rect.width, rect.height) * 0.75,
  );
  light.addColorStop(0, "rgba(255, 255, 255, 0.58)");
  light.addColorStop(0.55, "rgba(255, 255, 255, 0.1)");
  light.addColorStop(1, "rgba(17, 17, 17, 0.12)");
  ctx.fillStyle = light;
  ctx.fillRect(rect.x, rect.y, rect.width, rect.height);

  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();
  ctx.strokeStyle = "rgba(17, 17, 17, 0.08)";
  ctx.lineWidth = 0.55;
  const random = makeRandom(0x51a7e);
  for (let index = 0; index < 90; index += 1) {
    const x = rect.x + random() * rect.width;
    const y = rect.y + random() * rect.height;
    const length = 8 + random() * 38;
    ctx.beginPath();
    line(ctx, { x, y }, { x: x + length, y: y + (random() - 0.5) * 2.5 });
    ctx.stroke();
  }
  ctx.fillStyle = rgba(INK, 0.035 + roughness * 0.06);
  for (let index = 0; index < 1100; index += 1) {
    const size = 0.22 + random() * 0.7;
    ctx.fillRect(rect.x + random() * rect.width, rect.y + random() * rect.height, size, size);
  }
  ctx.restore();
}

function paintShader(ctx: CanvasRenderingContext2D, effect: TwinEffect & { kind: "shader" }, rect: Rect): void {
  const shader = effect.shader;
  const paper = controlColour(effect, "paper", SHEET);
  const colours = shaderPalette(controlColour(effect, "color", ""));
  const grain = clamp(controlNumber(effect, "grain", 0.2), 0, 1);
  const roughness = clamp(controlNumber(effect, "roughness", 0.45), 0, 1);
  const random = makeRandom(shader.length * 7919 + effect.preset.length);

  if (shader === "PaperTexture" || shader === "Fur") {
    paintPaperShader(ctx, effect, rect);
    return;
  }

  fillBase(ctx, rect, paper);
  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();

  if (shader === "MeshGradient" || shader === "GemSmoke") {
    for (let index = 0; index < 7; index += 1) {
      const colour = colours[index % colours.length];
      const cx = rect.x + rect.width * (0.12 + random() * 0.76);
      const cy = rect.y + rect.height * (0.12 + random() * 0.76);
      const radius = Math.max(rect.width, rect.height) * (0.18 + random() * 0.3);
      const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      gradient.addColorStop(0, rgba(colour, shader === "GemSmoke" ? 0.92 : 0.95));
      gradient.addColorStop(1, rgba(colour, 0));
      ctx.globalAlpha = shader === "GemSmoke" ? 0.82 : 0.88;
      ctx.fillStyle = gradient;
      ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
    }
  } else if (shader === "GrainGradient") {
    const gradient = ctx.createLinearGradient(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height);
    colours.forEach((colour, index) => gradient.addColorStop(index / Math.max(1, colours.length - 1), colour));
    ctx.fillStyle = gradient;
    ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
    ctx.fillStyle = rgba(INK, 0.04 + grain * 0.12);
    for (let index = 0; index < 1400; index += 1) {
      ctx.fillRect(rect.x + random() * rect.width, rect.y + random() * rect.height, 0.8, 0.8);
    }
  } else if (shader === "Warp") {
    ctx.lineWidth = 2.2;
    for (let index = 0; index < 28; index += 1) {
      const y = rect.y + (index / 27) * rect.height;
      ctx.strokeStyle = colours[index % colours.length];
      ctx.beginPath();
      for (let step = 0; step <= 18; step += 1) {
        const x = rect.x + (step / 18) * rect.width;
        const wave = Math.sin(step * 0.75 + index * 0.9) * rect.height * 0.035;
        if (step === 0) ctx.moveTo(x, y + wave);
        else ctx.lineTo(x, y + wave);
      }
      ctx.stroke();
    }
  } else if (shader === "DotOrbit") {
    const step = Math.max(7, rect.width / 38);
    for (let y = rect.y; y < rect.y + rect.height; y += step) {
      for (let x = rect.x; x < rect.x + rect.width; x += step) {
        const dx = x - (rect.x + rect.width / 2);
        const dy = y - (rect.y + rect.height / 2);
        const distance = Math.hypot(dx, dy) / Math.max(rect.width, rect.height);
        if (distance > 0.5) continue;
        ctx.fillStyle = colours[Math.floor((Math.atan2(dy, dx) + Math.PI) / (Math.PI * 2) * colours.length) % colours.length];
        ctx.beginPath();
        ctx.arc(x, y, step * (0.18 + (1 - distance) * 0.18), 0, Math.PI * 2);
        ctx.fill();
      }
    }
  } else if (shader === "ColorPanels") {
    ctx.fillStyle = colours[0];
    ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
    for (let index = 0; index < 12; index += 1) {
      ctx.save();
      ctx.translate(rect.x + rect.width / 2, rect.y + rect.height / 2);
      ctx.rotate(-0.6 + index * 0.13);
      ctx.fillStyle = rgba(colours[(index + 1) % colours.length], 0.8);
      ctx.fillRect(-rect.width * 0.7, -rect.height * 0.07 + index * rect.height * 0.055, rect.width * 1.4, rect.height * 0.032);
      ctx.restore();
    }
  } else if (shader === "LiquidMetal") {
    const gradient = ctx.createLinearGradient(rect.x, rect.y + rect.height, rect.x + rect.width, rect.y);
    [0, 0.16, 0.3, 0.48, 0.62, 0.8, 1].forEach((stop, index) => {
      gradient.addColorStop(stop, colours[index % colours.length]);
    });
    ctx.fillStyle = gradient;
    ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
    ctx.strokeStyle = rgba(SHEET, 0.45);
    ctx.lineWidth = 1.4;
    for (let index = 0; index < 18; index += 1) {
      ctx.beginPath();
      ctx.ellipse(rect.x + rect.width / 2, rect.y + rect.height / 2, rect.width * (0.12 + index * 0.025), rect.height * (0.08 + index * 0.018), 0.4, 0, Math.PI * 2);
      ctx.stroke();
    }
  } else if (shader === "FlutedGlass") {
    const step = Math.max(5, rect.width / 34);
    for (let index = 0; index * step < rect.width; index += 1) {
      ctx.fillStyle = rgba(index % 2 === 0 ? SHEET : INK, index % 2 === 0 ? 0.3 : 0.1);
      ctx.fillRect(rect.x + index * step, rect.y, step * 0.52, rect.height);
    }
    ctx.strokeStyle = rgba(INK, 0.12 + roughness * 0.12);
    ctx.lineWidth = 0.8;
    for (let index = 0; index < 60; index += 1) {
      const x = rect.x + random() * rect.width;
      ctx.beginPath();
      line(ctx, { x, y: rect.y }, { x, y: rect.y + rect.height });
      ctx.stroke();
    }
  } else if (shader === "ImageDithering" || shader === "HalftoneDots") {
    const step = Math.max(4, rect.width / (shader === "HalftoneDots" ? 58 : 78));
    for (let y = rect.y + step / 2; y < rect.y + rect.height; y += step) {
      for (let x = rect.x + step / 2; x < rect.x + rect.width; x += step) {
        const nx = (x - rect.x) / rect.width - 0.5;
        const ny = (y - rect.y) / rect.height - 0.5;
        const light = 1 - clamp(Math.hypot(nx, ny) * 1.5, 0, 1);
        const radius = step * (0.12 + light * 0.35);
        ctx.fillStyle = colours[0];
        ctx.beginPath();
        ctx.arc(x, y, radius, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  } else {
    paintPaperShader(ctx, effect, rect);
  }
  ctx.restore();
}

function paintPattern(
  ctx: CanvasRenderingContext2D,
  effect: TwinEffect & { kind: "pattern" },
  rect: Rect,
): void {
  const ink = controlColour(effect, "ink", INK);
  const opacity = clamp(controlNumber(effect, "opacity", 0.8), 0, 1);
  const scale = clamp(controlNumber(effect, "scale", 1), 0.2, 4);
  const step = Math.max(4, 20 * scale);
  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();
  ctx.globalAlpha = opacity;
  ctx.strokeStyle = ink;
  ctx.fillStyle = ink;
  ctx.lineWidth = 1;

  if (effect.preset === "jigsaw") {
    for (let y = rect.y - step; y < rect.y + rect.height + step; y += step) {
      for (let x = rect.x - step; x < rect.x + rect.width + step; x += step) {
        ctx.beginPath();
        ctx.arc(x, y, step * 0.34, 0, Math.PI * 2);
        ctx.stroke();
      }
    }
  } else if (effect.preset === "topography") {
    for (let index = 0; index < 9; index += 1) {
      ctx.beginPath();
      ctx.ellipse(
        rect.x + rect.width * 0.35,
        rect.y + rect.height * 0.42,
        step * (0.9 + index * 0.72),
        step * (0.62 + index * 0.5),
        -0.28,
        0,
        Math.PI * 2,
      );
      ctx.stroke();
    }
  } else if (effect.preset === "circuit") {
    for (let y = rect.y; y < rect.y + rect.height; y += step) {
      ctx.beginPath();
      line(ctx, { x: rect.x, y }, { x: rect.x + rect.width, y });
      ctx.stroke();
    }
    for (let x = rect.x; x < rect.x + rect.width; x += step) {
      ctx.beginPath();
      line(ctx, { x, y: rect.y }, { x, y: rect.y + rect.height });
      ctx.stroke();
    }
    for (let y = rect.y + step; y < rect.y + rect.height; y += step * 2) {
      for (let x = rect.x + step; x < rect.x + rect.width; x += step * 2) {
        ctx.beginPath();
        ctx.arc(x, y, 1.6, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  } else if (effect.preset === "diagonal") {
    for (let offset = -rect.height; offset < rect.width; offset += step) {
      ctx.beginPath();
      line(ctx, { x: rect.x + offset, y: rect.y + rect.height }, { x: rect.x + offset + rect.height, y: rect.y });
      ctx.stroke();
    }
  } else if (effect.preset === "plus") {
    for (let y = rect.y; y < rect.y + rect.height; y += step) {
      for (let x = rect.x; x < rect.x + rect.width; x += step) {
        ctx.beginPath();
        line(ctx, { x: x - step * 0.22, y }, { x: x + step * 0.22, y });
        line(ctx, { x, y: y - step * 0.22 }, { x, y: y + step * 0.22 });
        ctx.stroke();
      }
    }
  } else if (effect.preset === "rain") {
    for (let y = rect.y - step; y < rect.y + rect.height; y += step) {
      for (let x = rect.x - step; x < rect.x + rect.width; x += step) {
        ctx.beginPath();
        line(ctx, { x, y }, { x: x + step * 0.18, y: y + step * 0.55 });
        ctx.stroke();
      }
    }
  } else if (effect.preset === "bubbles") {
    const random = makeRandom(0xb0bb1e);
    for (let index = 0; index < 70; index += 1) {
      ctx.beginPath();
      ctx.arc(
        rect.x + random() * rect.width,
        rect.y + random() * rect.height,
        1.5 + random() * step * 0.34,
        0,
        Math.PI * 2,
      );
      ctx.stroke();
    }
  } else if (effect.preset === "aztec") {
    for (let y = rect.y; y < rect.y + rect.height; y += step) {
      const row = Math.floor((y - rect.y) / step);
      ctx.beginPath();
      let first = true;
      for (let x = rect.x; x <= rect.x + rect.width; x += step) {
        const pointY = y + ((Math.floor((x - rect.x) / step) + row) % 2 === 0 ? 0 : step * 0.35);
        if (first) {
          ctx.moveTo(x, pointY);
          first = false;
        } else {
          ctx.lineTo(x, pointY);
        }
      }
      ctx.stroke();
    }
  }
  ctx.restore();
}

function paintParticles(
  ctx: CanvasRenderingContext2D,
  effect: TwinEffect & { kind: "particles" },
  rect: Rect,
): void {
  const presets: Record<string, { count: number; size: number; colour: string; shape: "dot" | "square" | "streak" }> = {
    dust: { count: 80, size: 3, colour: "#5a5a56", shape: "dot" },
    sparks: { count: 64, size: 2, colour: INK, shape: "streak" },
    orbit: { count: 48, size: 3, colour: INK, shape: "dot" },
    confetti: { count: 42, size: 5, colour: "#5a5a56", shape: "square" },
  };
  const preset = presets[effect.preset] ?? presets.dust;
  const density = clamp(controlNumber(effect, "density", 1), 0.3, 2);
  const count = Math.round(preset.count * density);
  const colour = controlColour(effect, "colour", preset.colour);
  const random = makeRandom(0x9a71 + effect.preset.length);
  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();
  ctx.fillStyle = colour;
  for (let index = 0; index < count; index += 1) {
    const orbit = effect.preset === "orbit";
    const angle = (index / count) * Math.PI * 2 + 0.4;
    const radius = 0.34 + (index % 4) * 0.035;
    const x = orbit
      ? rect.x + rect.width * (0.5 + Math.cos(angle) * radius)
      : rect.x + random() * rect.width;
    const y = orbit
      ? rect.y + rect.height * (0.5 + Math.sin(angle) * radius)
      : rect.y + random() * rect.height;
    const width = preset.size * 0.55;
    const height = preset.shape === "streak" ? preset.size * 2.2 : preset.size * 0.55;
    if (preset.shape === "dot") {
      ctx.beginPath();
      ctx.arc(x, y, width, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.save();
      ctx.translate(x, y);
      ctx.fillRect(-width / 2, -height / 2, width, height);
      ctx.restore();
    }
  }
  ctx.restore();
}

function drawCover(ctx: CanvasRenderingContext2D, image: CanvasImageSource, rect: Rect): void {
  const source = image as CanvasImageSource & { width: number; height: number };
  const scale = Math.max(rect.width / source.width, rect.height / source.height);
  const width = source.width * scale;
  const height = source.height * scale;
  ctx.drawImage(image, rect.x + (rect.width - width) / 2, rect.y + (rect.height - height) / 2, width, height);
}

function paintTexture(
  ctx: CanvasRenderingContext2D,
  effect: TwinEffect & { kind: "texture" },
  rect: Rect,
  images: LoadedImages,
): void {
  const opacity = clamp(controlNumber(effect, "opacity", 1), 0, 1);
  const scale = clamp(controlNumber(effect, "scale", 1), 0.5, 2.5);
  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();
  ctx.globalAlpha *= opacity;

  if (effect.preset === "Fur" && !effect.image) {
    const paper = controlColour(effect, "paper", SHEET);
    fillBase(ctx, rect, paper);
    const random = makeRandom(0xf00d);
    ctx.strokeStyle = "rgba(17, 17, 17, 0.42)";
    ctx.lineWidth = 0.9;
    for (let index = 0; index < 260 * scale; index += 1) {
      const x = rect.x + random() * rect.width;
      const y = rect.y + random() * rect.height;
      const length = 3 + random() * 12 * scale;
      ctx.beginPath();
      line(ctx, { x, y }, { x: x + length, y: y + length * 0.75 });
      ctx.stroke();
    }
    const vignette = ctx.createRadialGradient(
      rect.x + rect.width / 2,
      rect.y + rect.height / 2,
      rect.width * 0.1,
      rect.x + rect.width / 2,
      rect.y + rect.height / 2,
      rect.width * 0.7,
    );
    vignette.addColorStop(0, "rgba(17, 17, 17, 0)");
    vignette.addColorStop(1, "rgba(17, 17, 17, 0.18)");
    ctx.fillStyle = vignette;
    ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
  } else {
    const image = effect.image ? images.get(effect.image) : null;
    if (image) {
      ctx.filter = "grayscale(1)";
      drawCover(ctx, image, rect);
      ctx.filter = "none";
      ctx.save();
      ctx.globalAlpha *= 0.48;
      ctx.globalCompositeOperation = "multiply";
      paintShader(ctx, { ...effect, kind: "shader", shader: effect.preset, controls: effect.controls }, rect);
      ctx.restore();
    } else {
      paintShader(ctx, { ...effect, kind: "shader", shader: effect.preset, controls: effect.controls }, rect);
    }
  }
  ctx.restore();
}

function paintEffect(
  ctx: CanvasRenderingContext2D,
  effect: TwinEffect | null,
  rect: Rect,
  images: LoadedImages,
): void {
  if (!effect) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(rect.x, rect.y, rect.width, rect.height);
  ctx.clip();
  if (effect.kind === "solid") {
    fillBase(ctx, rect, effect.hex);
  } else if (effect.kind === "shader") {
    paintShader(ctx, effect, rect);
  } else if (effect.kind === "pattern") {
    paintPattern(ctx, effect, rect);
  } else if (effect.kind === "particles") {
    paintParticles(ctx, effect, rect);
  } else if (effect.kind === "texture") {
    paintTexture(ctx, effect, rect, images);
  } else {
    const colours = effect.colors.length ? effect.colors : [SHEET, "#e7e6e1", "#8a8983", INK];
    fillBase(ctx, rect, colours[0]);
    const gradients = [
      { x: 0.5, y: 0.26, colour: colours[0] },
      { x: 0.77, y: 0.5, colour: colours[1] },
      { x: 0.5, y: 0.74, colour: colours[2 % colours.length] },
      { x: 0.23, y: 0.5, colour: colours[3 % colours.length] },
    ];
    for (const item of gradients) {
      const cx = rect.x + rect.width * item.x;
      const cy = rect.y + rect.height * item.y;
      const radius = Math.max(rect.width, rect.height) * 0.34;
      const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      gradient.addColorStop(0, item.colour);
      gradient.addColorStop(0.55, rgba(item.colour, 0.6));
      gradient.addColorStop(1, rgba(item.colour, 0));
      ctx.fillStyle = gradient;
      ctx.fillRect(rect.x, rect.y, rect.width, rect.height);
    }
  }
  ctx.restore();
}

function layerSize(layer: TwinLayer): { width: number; height: number } {
  return baseLayerSize(layer);
}

function paintTextLayer(ctx: CanvasRenderingContext2D, layer: TwinLayer): void {
  if (!layer.text) return;
  const size = layer.text.size;
  const lineHeight = size * 0.82;
  const lines = layer.text.content.split(/\r?\n/);
  const width = baseLayerSize(layer).width;
  const totalHeight = lineHeight * Math.max(1, lines.length);
  const align = layer.text.align;
  const x = align === "left" ? -width / 2 : align === "right" ? width / 2 : 0;
  ctx.save();
  ctx.font = `${size}px ${textFontStack(layer.text.font)}`;
  ctx.textAlign = align;
  ctx.textBaseline = "middle";
  ctx.fillStyle = layer.fill ?? INK;
  ctx.shadowColor = "rgba(250, 250, 247, 0.72)";
  ctx.shadowBlur = 0;
  ctx.shadowOffsetX = 0;
  ctx.shadowOffsetY = 1;
  (ctx as CanvasRenderingContext2D & { letterSpacing?: string }).letterSpacing = `${-0.045 * size}px`;
  lines.forEach((text, index) => {
    const y = -totalHeight / 2 + lineHeight / 2 + index * lineHeight;
    ctx.fillText(text || "\u00a0", x, y);
  });
  ctx.restore();
}

function starPath(ctx: CanvasRenderingContext2D, width: number, height: number): void {
  const points = [
    [50, 4], [61, 34], [93, 35], [68, 55], [77, 86], [50, 68], [23, 86], [32, 55], [7, 35], [39, 34],
  ];
  ctx.beginPath();
  points.forEach(([x, y], index) => {
    const px = -width / 2 + (x / 100) * width;
    const py = -height / 2 + (y / 100) * height;
    if (index === 0) ctx.moveTo(px, py);
    else ctx.lineTo(px, py);
  });
  ctx.closePath();
}

function paintShapeLayer(ctx: CanvasRenderingContext2D, layer: TwinLayer): void {
  const variant: ShapeVariant = layer.shape?.variant ?? (layer.id.includes("orbit") ? "orbit" : layer.id.includes("star") ? "star" : "block");
  const fill = layer.fill ?? INK;
  const size = baseLayerSize(layer).width;
  if (variant === "star") {
    const scale = size / 92;
    ctx.save();
    ctx.translate(2 * scale, 3 * scale);
    ctx.fillStyle = "rgba(17, 17, 17, 0.16)";
    starPath(ctx, size, size);
    ctx.fill();
    ctx.restore();
    ctx.fillStyle = fill;
    starPath(ctx, size, size);
    ctx.fill();
    return;
  }
  if (variant === "orbit") {
    const scale = size / 98;
    ctx.save();
    ctx.translate(4 * scale, 4 * scale);
    ctx.fillStyle = "rgba(17, 17, 17, 0.08)";
    ctx.beginPath();
    ctx.arc(0, 0, 49 * scale, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    ctx.strokeStyle = fill;
    ctx.lineWidth = 18 * scale;
    ctx.beginPath();
    ctx.arc(0, 0, 40 * scale, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = PAPER;
    ctx.beginPath();
    ctx.arc(0, 0, 9 * scale, 0, Math.PI * 2);
    ctx.fill();
    return;
  }
  const half = size / 2;
  const offset = (4 / 108) * size;
  ctx.save();
  ctx.fillStyle = "rgba(17, 17, 17, 0.1)";
  ctx.fillRect(-half + offset, -half + offset, size, size);
  ctx.restore();
  ctx.fillStyle = fill;
  ctx.fillRect(-half, -half, size, size);
  ctx.strokeStyle = "rgba(17, 17, 17, 0.24)";
  ctx.lineWidth = 1;
  ctx.strokeRect(-half, -half, size, size);
}

function paintImageLayer(ctx: CanvasRenderingContext2D, layer: TwinLayer, images: LoadedImages): void {
  if (!layer.image) return;
  const image = images.get(layer.image.src);
  if (!image) return;
  const width = layer.image.width;
  const height = layer.image.height;
  ctx.drawImage(image, -width / 2, -height / 2, width, height);
}

function paintLayer(
  ctx: CanvasRenderingContext2D,
  layer: TwinLayer,
  images: LoadedImages,
): void {
  const size = layerSize(layer);
  if (size.width <= 0 || size.height <= 0) return;
  if (layer.kind === "text") paintTextLayer(ctx, layer);
  else if (layer.kind === "shape") paintShapeLayer(ctx, layer);
  else paintImageLayer(ctx, layer, images);
  if (!layer.effect) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(-size.width / 2, -size.height / 2, size.width, size.height);
  ctx.clip();
  ctx.globalAlpha = 0.55;
  ctx.globalCompositeOperation = "multiply";
  paintEffect(
    ctx,
    layer.effect,
    { x: -size.width / 2, y: -size.height / 2, width: size.width, height: size.height },
    images,
  );
  ctx.restore();
}

function paintArtboardGrain(ctx: CanvasRenderingContext2D): void {
  const random = makeRandom(0xa27b0a4d);
  ctx.save();
  ctx.globalCompositeOperation = "multiply";
  ctx.globalAlpha = 0.55;
  ctx.fillStyle = "rgba(17, 17, 17, 0.045)";
  for (let index = 0; index < 1200; index += 1) {
    ctx.fillRect(random() * ARTBOARD_DESIGN_WIDTH, random() * ARTBOARD_DESIGN_HEIGHT, 0.4, 0.4);
  }
  const wash = ctx.createLinearGradient(0, 0, ARTBOARD_DESIGN_WIDTH, ARTBOARD_DESIGN_HEIGHT);
  wash.addColorStop(0, "rgba(255, 255, 255, 0.08)");
  wash.addColorStop(0.42, "rgba(255, 255, 255, 0)");
  wash.addColorStop(1, "rgba(17, 17, 17, 0.03)");
  ctx.fillStyle = wash;
  ctx.fillRect(0, 0, ARTBOARD_DESIGN_WIDTH, ARTBOARD_DESIGN_HEIGHT);
  ctx.restore();
}

function paintMeta(ctx: CanvasRenderingContext2D): void {
  const metaScale = ARTBOARD_DESIGN_WIDTH / 338;
  ctx.save();
  ctx.fillStyle = "rgba(17, 17, 17, 0.72)";
  ctx.font = `${6 * metaScale}px "Libre Franklin", system-ui, sans-serif`;
  ctx.textBaseline = "alphabetic";
  (ctx as CanvasRenderingContext2D & { letterSpacing?: string }).letterSpacing = `${0.72 * metaScale}px`;
  ctx.textAlign = "left";
  ctx.fillText("VOL. 01", 12 * metaScale, ARTBOARD_DESIGN_HEIGHT - 9 * metaScale);
  ctx.textAlign = "right";
  ctx.fillText("PRINT / 2026", ARTBOARD_DESIGN_WIDTH - 12 * metaScale, ARTBOARD_DESIGN_HEIGHT - 9 * metaScale);
  ctx.strokeStyle = "rgba(17, 17, 17, 0.48)";
  ctx.lineWidth = 1;
  ctx.strokeRect(0.5, 0.5, ARTBOARD_DESIGN_WIDTH - 1, ARTBOARD_DESIGN_HEIGHT - 1);
  ctx.restore();
}

function paintArtboard(
  ctx: CanvasRenderingContext2D,
  plan: ArtboardRenderPlan,
  images: LoadedImages,
  createCanvas: (width: number, height: number) => HTMLCanvasElement,
): void {
  ctx.clearRect(0, 0, plan.width, plan.height);
  fillBase(ctx, { x: 0, y: 0, width: plan.width, height: plan.height }, SHEET);

  ctx.save();
  ctx.scale(plan.scale, plan.scale);
  paintEffect(
    ctx,
    plan.background,
    { x: 0, y: 0, width: plan.designWidth, height: plan.designHeight },
    images,
  );
  paintArtboardGrain(ctx);
  ctx.restore();

  const layerCanvas = createCanvas(plan.width, plan.height);
  const layerContext = layerCanvas.getContext("2d");
  if (!layerContext) throw new Error("The PNG layer could not be created.");

  for (const layer of plan.layers) {
    layerContext.clearRect(0, 0, plan.width, plan.height);
    layerContext.save();
    layerContext.translate(
      plan.width / 2 + layer.transform.x * plan.scale,
      plan.height / 2 + layer.transform.y * plan.scale,
    );
    layerContext.rotate((layer.transform.rotation * Math.PI) / 180);
    layerContext.scale(layer.transform.scaleX * plan.scale, layer.transform.scaleY * plan.scale);
    paintLayer(layerContext, layer, images);
    layerContext.restore();

    ctx.save();
    ctx.globalAlpha = clamp(layer.opacity, 0, 1);
    ctx.globalCompositeOperation = blendOperation(layer.blend);
    ctx.drawImage(layerCanvas, 0, 0);
    ctx.restore();
  }

  ctx.save();
  ctx.scale(plan.scale, plan.scale);
  paintMeta(ctx);
  ctx.restore();
}

async function waitForFonts(plan: ArtboardRenderPlan): Promise<void> {
  if (typeof document === "undefined" || !document.fonts) return;
  const faces = new Set(
    plan.layers
      .filter((layer) => layer.kind === "text" && layer.text)
      .map((layer) => `${layer.text?.size ?? 68}px ${textFontStack(layer.text?.font)}`),
  );
  try {
    await document.fonts.ready;
    await Promise.all([...faces].map((face) => document.fonts.load(face)));
  } catch {
    // A missing webfont still produces a valid PNG with the fallback face.
  }
}

async function loadDocumentImages(
  plan: ArtboardRenderPlan,
  loadImage: (src: string) => Promise<CanvasImageSource | null>,
): Promise<LoadedImages> {
  const sources = new Set<string>();
  if (plan.background?.kind === "texture" && plan.background.image) sources.add(plan.background.image);
  for (const layer of plan.layers) {
    if (layer.image?.src) sources.add(layer.image.src);
    if (layer.effect?.kind === "texture" && layer.effect.image) sources.add(layer.effect.image);
  }
  const entries = await Promise.all([...sources].map(async (src) => [src, await loadImage(src)] as const));
  return new Map(entries);
}

function defaultLoadImage(src: string): Promise<CanvasImageSource | null> {
  return new Promise((resolve) => {
    if (typeof Image === "undefined") {
      resolve(null);
      return;
    }
    const image = new Image();
    image.decoding = "async";
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = src;
  });
}

async function blobBytes(blob: Blob): Promise<Uint8Array> {
  if (typeof blob.arrayBuffer === "function") return new Uint8Array(await blob.arrayBuffer());
  if (typeof FileReader === "undefined") throw new Error("The PNG could not be read back.");
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(new Error("The PNG could not be read back."));
    reader.readAsArrayBuffer(blob);
  });
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    if (typeof canvas.toBlob !== "function") {
      reject(new Error("This browser cannot encode a PNG."));
      return;
    }
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("The PNG could not be encoded."));
    }, PNG_MIME);
  });
}

export async function renderArtboardPng(
  doc: TwinDocument,
  dependencies: PngExportDependencies = {},
): Promise<PngExportResult> {
  const plan = buildArtboardRenderPlan(doc);
  await waitForFonts(plan);
  const images = await loadDocumentImages(plan, dependencies.loadImage ?? defaultLoadImage);
  const createCanvas = dependencies.createCanvas ?? ((width: number, height: number) => {
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    return canvas;
  });
  const canvas = createCanvas(plan.width, plan.height);
  canvas.width = plan.width;
  canvas.height = plan.height;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("The PNG canvas could not be created.");
  paintArtboard(context, plan, images, createCanvas);
  const blob = await canvasToBlob(canvas);
  const bytes = await blobBytes(blob);
  const info = readPngInfo(bytes);
  if (!info) throw new Error("The PNG encoder returned an invalid file.");
  if (info.width !== plan.width || info.height !== plan.height) {
    throw new Error("The PNG dimensions do not match the artboard.");
  }
  return {
    blob,
    bytes: blob.size,
    filename: plan.filename,
    ...info,
  };
}
