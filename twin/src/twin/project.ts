import { sanitizeSvgMarkup } from "./imports";
import { MAX_IMAGE_LAYERS, MAX_LAYERS } from "../editor/model/geometry";
import { BLEND_MODES, TEXT_FONTS } from "./types";
import type {
  BlendMode,
  ShapeVariant,
  TextAlign,
  TextFont,
  TwinDocument,
  TwinEffect,
  TwinLayer,
} from "./types";

export const PROJECT_FORMAT = "bmo-twin-project";
export const PROJECT_VERSION = 2;
export const PROJECT_EXTENSION = ".twin";
export const MAX_PROJECT_BYTES = 64 * 1024 * 1024;
const MAX_LAYER_TEXT = 2000;
const MIGRATION_FACTOR = 765 / 338;
const MAX_EFFECT_CONTROLS = 32;

export interface TwinProjectFile {
  format: typeof PROJECT_FORMAT;
  version: typeof PROJECT_VERSION;
  savedAt: string;
  document: TwinDocument;
}

export class ProjectFileError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ProjectFileError";
  }
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const finite = (value: unknown, label: string): number => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ProjectFileError(`${label} must be a finite number.`);
  }
  return value;
};

const bounded = (value: unknown, min: number, max: number, label: string): number => {
  const number = finite(value, label);
  if (number < min || number > max) {
    throw new ProjectFileError(`${label} must be between ${min} and ${max}.`);
  }
  return number;
};

const text = (value: unknown, label: string, maxLength = 200): string => {
  if (typeof value !== "string" || value.length > maxLength) {
    throw new ProjectFileError(`${label} is not valid.`);
  }
  return value;
};

const hex = (value: unknown, label: string): string => {
  const colour = text(value, label, 9);
  if (!/^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(colour)) {
    throw new ProjectFileError(`${label} must be a hex colour.`);
  }
  return colour;
};

const blend = (value: unknown): BlendMode => {
  if (typeof value !== "string" || !(BLEND_MODES as readonly string[]).includes(value)) {
    throw new ProjectFileError("A layer has an unsupported blend mode.");
  }
  return value as BlendMode;
};

const textFont = (value: unknown): TextFont => {
  if (typeof value !== "string" || !TEXT_FONTS.some((font) => font.id === value)) {
    throw new ProjectFileError("A text layer has an unsupported font.");
  }
  return value as TextFont;
};

const textAlign = (value: unknown): TextAlign => {
  if (value !== "left" && value !== "center" && value !== "right") {
    throw new ProjectFileError("A text layer has an unsupported alignment.");
  }
  return value;
};

const shapeVariant = (value: unknown): ShapeVariant => {
  if (value !== "star" && value !== "orbit" && value !== "block") {
    throw new ProjectFileError("A shape layer has an unsupported variant.");
  }
  return value;
};

function sanitizeControls(value: unknown): Record<string, number | string> {
  if (!isRecord(value)) throw new ProjectFileError("An effect has invalid controls.");
  const entries = Object.entries(value);
  if (entries.length > MAX_EFFECT_CONTROLS) throw new ProjectFileError("An effect has too many controls.");
  const controls: Record<string, number | string> = {};
  for (const [key, control] of entries) {
    if (key.length > 40) throw new ProjectFileError("An effect control name is too long.");
    if (typeof control === "number") {
      if (!Number.isFinite(control)) throw new ProjectFileError("An effect control is not finite.");
      controls[key] = control;
    } else if (typeof control === "string" && control.length <= 200) {
      if (["color", "colour", "paper", "ink", "background", "fill"].includes(key) && !/^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(control)) {
        throw new ProjectFileError(`Effect control ${key} must be a hex colour.`);
      }
      controls[key] = control;
    } else {
      throw new ProjectFileError("An effect control is not valid.");
    }
  }
  return controls;
}

function sanitizeEffect(value: unknown): TwinEffect | null {
  if (value === null) return null;
  if (!isRecord(value)) throw new ProjectFileError("An effect is not valid.");
  if (value.kind === "solid") {
    return { kind: "solid", hex: hex(value.hex, "Solid effect colour") };
  }
  if (value.kind === "field") {
    if (!Array.isArray(value.colors) || value.colors.length < 1 || value.colors.length > 6) {
      throw new ProjectFileError("A field effect needs between one and six colours.");
    }
    return {
      kind: "field",
      preset: text(value.preset, "Field preset"),
      colors: value.colors.map((colour, index) => hex(colour, `Field colour ${index + 1}`)),
    };
  }
  if (value.kind === "shader") {
    return {
      kind: "shader",
      shader: text(value.shader, "Shader name"),
      preset: text(value.preset, "Shader preset"),
      controls: sanitizeControls(value.controls),
    };
  }
  if (value.kind === "texture") {
    const image = value.image === undefined ? undefined : sanitizeImageSource(value.image, "Texture image", true);
    return {
      kind: "texture",
      preset: text(value.preset, "Texture preset"),
      ...(image ? { image } : {}),
      controls: sanitizeControls(value.controls),
    };
  }
  if (value.kind === "particles") {
    return {
      kind: "particles",
      preset: text(value.preset, "Particle preset"),
      controls: sanitizeControls(value.controls),
    };
  }
  if (value.kind === "pattern") {
    return {
      kind: "pattern",
      preset: text(value.preset, "Pattern preset"),
      controls: sanitizeControls(value.controls),
    };
  }
  throw new ProjectFileError("An effect uses an unsupported kind.");
}

function encodeBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function sanitizeImageSource(value: unknown, label: string, allowMaterial = false): string {
  const src = text(value, label, 24_000_000);
  const match = /^data:image\/(png|jpeg|webp|svg\+xml);base64,([a-z0-9+/]+=*)$/i.exec(src);
  if (!match) throw new ProjectFileError(`${label} must be an embedded image.`);
  const mime = match[1].toLowerCase();
  if (!allowMaterial && mime !== "png") {
    throw new ProjectFileError(`${label} must be an embedded PNG.`);
  }
  if (mime !== "svg+xml") return `data:image/${mime};base64,${match[2]}`;

  let markup: string;
  try {
    const bytes = Uint8Array.from(atob(match[2]), (character) => character.charCodeAt(0));
    markup = new TextDecoder().decode(bytes);
  } catch {
    throw new ProjectFileError(`${label} has invalid embedded SVG data.`);
  }
  const safe = sanitizeSvgMarkup(markup);
  return `data:image/svg+xml;base64,${encodeBase64(safe)}`;
}

function sanitizeLayer(value: unknown, index: number): TwinLayer {
  if (!isRecord(value)) throw new ProjectFileError(`Layer ${index + 1} is not valid.`);
  const id = text(value.id, `Layer ${index + 1} id`);
  if (!id) throw new ProjectFileError(`Layer ${index + 1} has no id.`);
  const kind = value.kind;
  if (kind !== "text" && kind !== "shape" && kind !== "image") {
    throw new ProjectFileError(`Layer ${index + 1} has an unsupported kind.`);
  }
  const transform = isRecord(value.transform) ? value.transform : {};
  const visible = value.visible;
  if (typeof visible !== "boolean") throw new ProjectFileError(`Layer ${index + 1} has invalid visibility.`);

  const layer: TwinLayer = {
    id,
    name: text(value.name, `Layer ${index + 1} name`, 120),
    kind,
    effect: sanitizeEffect(value.effect),
    blend: blend(value.blend),
    opacity: bounded(value.opacity, 0, 1, `Layer ${index + 1} opacity`),
    transform: {
      x: bounded(transform.x, -100_000, 100_000, `Layer ${index + 1} x`),
      y: bounded(transform.y, -100_000, 100_000, `Layer ${index + 1} y`),
      scaleX: bounded(transform.scaleX, 0.01, 100, `Layer ${index + 1} scaleX`),
      scaleY: bounded(transform.scaleY, 0.01, 100, `Layer ${index + 1} scaleY`),
      rotation: bounded(transform.rotation, -360_000, 360_000, `Layer ${index + 1} rotation`),
    },
    visible,
  };

  if (kind === "text") {
    if (!isRecord(value.text)) throw new ProjectFileError(`Layer ${index + 1} has no text.`);
    layer.text = {
      content: text(value.text.content, `Layer ${index + 1} text`, MAX_LAYER_TEXT),
      size: bounded(value.text.size, 8, 400, `Layer ${index + 1} text size`),
      align: textAlign(value.text.align),
      font: textFont(value.text.font),
    };
    layer.fill = hex(value.fill, `Layer ${index + 1} fill`);
  }

  if (kind === "shape") {
    if (!isRecord(value.shape)) throw new ProjectFileError(`Layer ${index + 1} has no shape.`);
    layer.shape = { variant: shapeVariant(value.shape.variant) };
    layer.fill = hex(value.fill, `Layer ${index + 1} fill`);
  }

  if (kind === "image") {
    if (!isRecord(value.image)) throw new ProjectFileError(`Layer ${index + 1} has no image.`);
    const src = sanitizeImageSource(value.image.src, `Layer ${index + 1} image`);
    const width = bounded(value.image.width, 1, 8192, `Layer ${index + 1} image width`);
    const height = bounded(value.image.height, 1, 8192, `Layer ${index + 1} image height`);
    if (width * height > 16_000_000) throw new ProjectFileError(`Layer ${index + 1} image is too large.`);
    layer.image = {
      assetId: text(value.image.assetId, `Layer ${index + 1} asset id`, 120),
      src,
      width,
      height,
    };
  }

  return layer;
}

export function validateDocument(value: unknown): TwinDocument {
  if (!isRecord(value)) throw new ProjectFileError("The project document is missing.");
  if (!Array.isArray(value.layers)) throw new ProjectFileError("The project layers are missing.");
  if (value.layers.length > MAX_LAYERS) throw new ProjectFileError("The project has too many layers.");
  const layers = value.layers.map(sanitizeLayer);
  if (layers.filter((layer) => layer.kind === "image").length > MAX_IMAGE_LAYERS) {
    throw new ProjectFileError("The project has more than 12 PNG layers.");
  }
  if (new Set(layers.map((layer) => layer.id)).size !== layers.length) {
    throw new ProjectFileError("The project contains duplicate layer ids.");
  }
  if (value.selectedLayerIds !== undefined && !Array.isArray(value.selectedLayerIds)) {
    throw new ProjectFileError("The project selection is not valid.");
  }
  const ids = Array.isArray(value.selectedLayerIds) ? value.selectedLayerIds : [];
  const selectedLayerIds = [...new Set(ids)]
    .filter((id): id is string => typeof id === "string")
    .filter((id) => layers.some((layer) => layer.id === id));
  return {
    background: sanitizeEffect(value.background),
    layers,
    selectedLayerIds,
  };
}

/**
 * v1 stored one selection and one uniform scale. Migrate the raw object into
 * the v2 shape first, then let the normal validator own every range check.
 */
export function migrateV1Document(value: unknown): TwinDocument {
  if (!isRecord(value)) throw new ProjectFileError("The v1 document is missing.");
  if (!Array.isArray(value.layers)) throw new ProjectFileError("The v1 document has no layers.");
  const layers = value.layers.map((raw) => {
    if (!isRecord(raw)) throw new ProjectFileError("A v1 layer is not valid.");
    const transform = isRecord(raw.transform) ? raw.transform : {};
    const scale = transform.scale;
    const migrated: Record<string, unknown> = {
      ...raw,
      transform: {
        x: Number(transform.x) * MIGRATION_FACTOR,
        y: Number(transform.y) * MIGRATION_FACTOR,
        scaleX: scale,
        scaleY: scale,
        rotation: transform.rotation,
      },
    };
    if (raw.kind === "text" && isRecord(raw.text)) {
      migrated.text = {
        ...raw.text,
        size: Number(raw.text.size) * MIGRATION_FACTOR,
      };
    }
    if (raw.kind === "image" && isRecord(raw.image)) {
      migrated.image = {
        ...raw.image,
        width: Number(raw.image.width) * MIGRATION_FACTOR,
        height: Number(raw.image.height) * MIGRATION_FACTOR,
      };
    }
    return migrated;
  });
  const selectedLayerId = typeof value.selectedLayerId === "string" ? value.selectedLayerId : "";
  return validateDocument({
    ...value,
    layers,
    selectedLayerIds: selectedLayerId ? [selectedLayerId] : [],
  });
}

export function parseProjectFile(source: string): TwinDocument {
  if (source.length > MAX_PROJECT_BYTES) throw new ProjectFileError("That project file is too large.");
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch {
    throw new ProjectFileError("That file is not valid JSON.");
  }
  if (!isRecord(parsed)) throw new ProjectFileError("That file is not a Twin project.");
  if (parsed.format !== PROJECT_FORMAT) {
    throw new ProjectFileError("That file is not a supported Twin project.");
  }
  if (parsed.version === 1) return migrateV1Document(parsed.document);
  if (parsed.version !== PROJECT_VERSION) {
    throw new ProjectFileError("That file is not a supported Twin project.");
  }
  return validateDocument(parsed.document);
}

function readAsDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new ProjectFileError("A PNG could not be read for saving."));
    reader.readAsDataURL(blob);
  });
}

async function inlineImageSource(src: string): Promise<string> {
  if (src.startsWith("data:image/png;base64,")) return src;
  const response = await fetch(src);
  if (!response.ok) throw new ProjectFileError("A PNG could not be read for saving.");
  const blob = await response.blob();
  if (blob.type !== "image/png") throw new ProjectFileError("Only embedded PNG layers can be saved.");
  return readAsDataUrl(blob);
}

export async function serializeProject(doc: TwinDocument): Promise<string> {
  const layers = await Promise.all(
    doc.layers.map(async (layer) => ({
      ...layer,
      effect: layer.effect ? structuredClone(layer.effect) : null,
      transform: { ...layer.transform },
      ...(layer.image
        ? { image: { ...layer.image, src: await inlineImageSource(layer.image.src) } }
        : {}),
    })),
  );
  const project: TwinProjectFile = {
    format: PROJECT_FORMAT,
    version: PROJECT_VERSION,
    savedAt: new Date().toISOString(),
    document: {
      ...doc,
      background: doc.background ? structuredClone(doc.background) : null,
      layers,
    },
  };
  return JSON.stringify(project, null, 2);
}

export function projectFilename(doc: TwinDocument): string {
  const headline = doc.layers.find((layer) => layer.kind === "text" && layer.text?.content.trim());
  const raw = headline?.text?.content ?? "twin-project";
  const slug = raw
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return `${slug || "twin-project"}${PROJECT_EXTENSION}`;
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = "noopener";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
