import type { ComponentType, ReactNode } from "react";

export type Reality = "native-ready" | "needs-native" | "browser-only";
export type Tier = 1 | 2 | 3;
export type Surface = "none" | "commands" | "plugins" | "inspector";
export type NativeTab = "tone" | "prism";
export type Layout = "compact" | "expanded";
export type EffectTarget = "artboard" | "layer";
export type TextAlign = "left" | "center" | "right";
export type ShapeVariant = "star" | "orbit" | "block";

export const TEXT_FONTS = [
  { id: "instrument-serif", name: "Instrument Serif", note: "Editorial display", stack: '"Instrument Serif", Georgia, serif' },
  { id: "spectral", name: "Spectral", note: "Book serif", stack: '"Spectral", Georgia, serif' },
  { id: "poppins", name: "Poppins", note: "Geometric sans", stack: '"Poppins", system-ui, sans-serif' },
  { id: "libre-franklin", name: "Libre Franklin", note: "Workhorse sans", stack: '"Libre Franklin", system-ui, sans-serif' },
] as const;

export type TextFont = (typeof TEXT_FONTS)[number]["id"];

export const DEFAULT_TEXT_FONT: TextFont = "instrument-serif";

export function textFontStack(font?: TextFont): string {
  return (TEXT_FONTS.find((item) => item.id === font) ?? TEXT_FONTS[0]).stack;
}

export const BLEND_MODES = [
  "normal",
  "multiply",
  "screen",
  "overlay",
  "darken",
  "lighten",
  "color-dodge",
  "color-burn",
  "hard-light",
  "soft-light",
  "difference",
  "exclusion",
  "hue",
  "saturation",
  "color",
  "luminosity",
] as const;

export type BlendMode = (typeof BLEND_MODES)[number];

export type TwinEffect =
  | {
      kind: "shader";
      shader: string;
      preset: string;
      controls: Record<string, number | string>;
    }
  | {
      kind: "texture";
      preset: string;
      image?: string;
      controls: Record<string, number | string>;
    }
  | {
      kind: "particles";
      preset: string;
      controls: Record<string, number | string>;
    }
  | {
      kind: "pattern";
      preset: string;
      controls: Record<string, number | string>;
    }
  | {
      kind: "field";
      preset: string;
      colors: string[];
    }
  | { kind: "solid"; hex: string }
  /**
   * An effect this build does not know, kept verbatim so the library can grow.
   *
   * The shelf is the source of effect presets, so a newer catalog will describe kinds
   * this build has never heard of. Opening such a file must degrade to a stand-in and
   * must never lose the payload: `raw` is exactly what was on disk, and it is written
   * back untouched. Without this, one new catalog entry would make every older client
   * refuse to open the file at all.
   */
  | {
      kind: "unsupported";
      originalKind: string;
      raw: Record<string, unknown>;
    };

export interface LayerTransform {
  x: number;
  y: number;
  scaleX: number;
  scaleY: number;
  rotation: number;
}

export interface TwinLayer {
  id: string;
  name: string;
  kind: "text" | "shape" | "image";
  effect: TwinEffect | null;
  blend: BlendMode;
  opacity: number;
  transform: LayerTransform;
  visible: boolean;
  image?: { assetId: string; src: string; width: number; height: number };
  text?: { content: string; size: number; align: TextAlign; font?: TextFont };
  shape?: { variant: ShapeVariant };
  fill?: string;
}

export interface TwinDocument {
  background: TwinEffect | null;
  layers: TwinLayer[];
  /** May be empty: a new or emptied document is valid. */
  selectedLayerIds: string[];
}

export interface PluginPanelProps {
  doc: TwinDocument;
  target: EffectTarget;
  setTarget: (target: EffectTarget) => void;
  apply: (effect: TwinEffect) => void;
  toast: (message: string) => void;
}

export interface TwinPlugin {
  id: string;
  name: string;
  glyph: ReactNode;
  group: "Effects" | "Texture" | "Overlay" | "Motion" | "Native";
  blurb: string;
  tags: string[];
  reality: Reality;
  tier: Tier;
  panel: ComponentType<PluginPanelProps>;
}

export interface TwinCommand {
  id: string;
  label: string;
  group: string;
  hint?: string;
  shortcut?: string;
  disabled?: boolean;
  run: () => void;
}

export const NATIVE_LENS_LABELS: Record<Reality, string> = {
  "native-ready": "NATIVE READY",
  "needs-native": "NEEDS NATIVE",
  "browser-only": "BROWSER ONLY",
};
