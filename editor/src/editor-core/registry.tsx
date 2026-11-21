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
import ShaderLabPlugin from "../plugins/ShaderLabPlugin";
import TextureLabPlugin from "../plugins/TextureLabPlugin";
import ParticleFieldPlugin from "../plugins/ParticleFieldPlugin";
import PatternLibraryPlugin from "../plugins/PatternLibraryPlugin";
import SpatialDockPlugin from "../plugins/SpatialDockPlugin";
import NativeParityPlugin from "../plugins/NativeParityPlugin";
import AssetShelfPlugin from "../plugins/AssetShelfPlugin";
import OverlayLabPlugin from "../plugins/OverlayLabPlugin";
import type { EditorPlugin } from "./types";

const glyph = (Icon: LucideIcon) => <Icon size={20} strokeWidth={1.6} />;

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
    panel: ShaderLabPlugin,
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
    panel: TextureLabPlugin,
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
    panel: ParticleFieldPlugin,
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
    panel: PatternLibraryPlugin,
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
    panel: SpatialDockPlugin,
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
    panel: NativeParityPlugin,
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
    panel: AssetShelfPlugin,
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
    panel: OverlayLabPlugin,
  },
];

export function getPlugin(id: string | null): EditorPlugin | undefined {
  return PLUGINS.find((plugin) => plugin.id === id);
}
