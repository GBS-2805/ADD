import { lazy } from "react";
import {
  Box,
  Grid3X3,
  Image,
  Layers,
  Orbit,
  SlidersHorizontal,
  Sparkles,
  Shapes,
  type LucideIcon,
} from "lucide-react";
import type { EditorPlugin } from "./types";

const glyph = (Icon: LucideIcon) => <Icon size={20} strokeWidth={1.6} />;

/**
 * Every panel used to be in the entry chunk even though startup shows the
 * catalogue, not a panel. Each panel is now its own chunk: the catalogue keeps the
 * metadata and glyph, and a panel's code arrives on hover, on focus, on click, or
 * on the render that opens it. The loaders are the single source for both `lazy`
 * and `preloadPlugin`, so a preload and the later render share one import.
 */
const loaders = {
  "shader-lab": () => import("../plugins/ShaderLabPlugin"),
  "texture-lab": () => import("../plugins/TextureLabPlugin"),
  "particle-field": () => import("../plugins/ParticleFieldPlugin"),
  "pattern-library": () => import("../plugins/PatternLibraryPlugin"),
  "spatial-dock": () => import("../plugins/SpatialDockPlugin"),
  "native-parity": () => import("../plugins/NativeParityPlugin"),
  "asset-shelf": () => import("../plugins/AssetShelfPlugin"),
  "overlay-lab": () => import("../plugins/OverlayLabPlugin"),
} as const;

type PluginId = keyof typeof loaders;

const lazyPanel = (id: PluginId) => lazy(loaders[id]);

export const PLUGINS: EditorPlugin[] = [
  {
    id: "shader-lab",
    name: "Shader Lab",
    glyph: glyph(Sparkles),
    group: "Effects",
    blurb: "Paper Shaders grounds in ink, grain and motion.",
    tags: ["shader", "gradient", "mesh", "grain", "warp", "paper"],
    reality: "needs-native",
    tier: 1,
    panel: lazyPanel("shader-lab"),
  },
  {
    id: "texture-lab",
    name: "Texture Lab",
    glyph: glyph(Layers),
    group: "Texture",
    blurb: "Paper, fluted glass, halftone and a coat of fur.",
    tags: ["texture", "paper", "fur", "halftone", "glass"],
    reality: "needs-native",
    tier: 1,
    panel: lazyPanel("texture-lab"),
  },
  {
    id: "particle-field",
    name: "Particle Field",
    glyph: glyph(Orbit),
    group: "Effects",
    blurb: "Dust, sparks, orbit and cut-paper confetti.",
    tags: ["particles", "dust", "sparks", "confetti", "motion"],
    reality: "browser-only",
    tier: 2,
    panel: lazyPanel("particle-field"),
  },
  {
    id: "pattern-library",
    name: "Pattern Library",
    glyph: glyph(Grid3X3),
    group: "Texture",
    blurb: "Eight repeatable printed grounds with a rough edge.",
    tags: ["pattern", "print", "repeat", "rough", "ink"],
    reality: "needs-native",
    tier: 1,
    panel: lazyPanel("pattern-library"),
  },
  {
    id: "spatial-dock",
    name: "Spatial Dock",
    glyph: glyph(Box),
    group: "Motion",
    blurb: "A local 3D object and two cards that tilt under the pointer.",
    tags: ["3d", "three", "motion", "tilt", "dock"],
    reality: "browser-only",
    tier: 3,
    panel: lazyPanel("spatial-dock"),
  },
  {
    id: "native-parity",
    name: "Native Parity",
    glyph: glyph(SlidersHorizontal),
    group: "Native",
    blurb: "Tone and Prism with the same labels as the native shelf.",
    tags: ["native", "tone", "prism", "parity", "colour"],
    reality: "native-ready",
    tier: 1,
    panel: lazyPanel("native-parity"),
  },
  {
    id: "asset-shelf",
    name: "Asset Shelf",
    glyph: glyph(Image),
    group: "Texture",
    blurb: "Paper, Lucide samples and safe local material.",
    tags: ["asset", "paper", "icon", "import", "svg"],
    reality: "needs-native",
    tier: 2,
    panel: lazyPanel("asset-shelf"),
  },
  {
    id: "overlay-lab",
    name: "Overlay Lab",
    glyph: glyph(Shapes),
    group: "Overlay",
    blurb: "PNG layers with order, transform, opacity and 16 blend modes.",
    tags: ["png", "layer", "blend", "overlay", "transform", "psd"],
    reality: "needs-native",
    tier: 1,
    panel: lazyPanel("overlay-lab"),
  },
];

export function getPlugin(id: string | null): EditorPlugin | undefined {
  return PLUGINS.find((plugin) => plugin.id === id);
}

/**
 * Starts a panel's import without opening it, and never throws. A preload the user
 * never opens has nobody to await it, so the rejection is marked handled here; the
 * panel's own render is where a chunk that really cannot load becomes visible.
 */
export function preloadPlugin(id: string): Promise<unknown> | undefined {
  const load = loaders[id as PluginId];
  if (!load) return undefined;
  const loading = load();
  void loading.catch(() => {});
  return loading;
}
