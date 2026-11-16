export interface ShaderPreset {
  id: string;
  name: string;
  shader: string;
  note: string;
}

export const SHADER_PRESETS: ShaderPreset[] = [
  { id: "mesh", name: "Mesh gradient", shader: "MeshGradient", note: "Soft ink field in motion" },
  { id: "grain", name: "Grain gradient", shader: "GrainGradient", note: "Inky bands with paper grain" },
  { id: "warp", name: "Warp", shader: "Warp", note: "Striped ink under pressure" },
  { id: "orbit", name: "Dot orbit", shader: "DotOrbit", note: "A field of small satellites" },
  { id: "panels", name: "Ink panels", shader: "ColorPanels", note: "A fan of printed sheets" },
  { id: "metal", name: "Liquid metal", shader: "LiquidMetal", note: "Chrome ink in a circular well" },
  { id: "smoke", name: "Gem smoke", shader: "GemSmoke", note: "A slow bloom of ink" },
  { id: "paper", name: "Paper texture", shader: "PaperTexture", note: "The workbench itself" },
];

export interface TexturePreset {
  id: string;
  name: string;
  shader: string;
  note: string;
}

export const TEXTURE_PRESETS: TexturePreset[] = [
  { id: "paper", name: "Paper 006", shader: "PaperTexture", note: "Neutral stock" },
  { id: "creased", name: "Fluted glass", shader: "FlutedGlass", note: "Ribbed transparency" },
  { id: "dither", name: "Image dither", shader: "ImageDithering", note: "A printed photograph" },
  { id: "halftone", name: "Halftone dots", shader: "HalftoneDots", note: "Newsprint at close range" },
  { id: "fur", name: "Fur", shader: "Fur", note: "A pettable silhouette" },
];

export interface ParticlePreset {
  id: string;
  name: string;
  note: string;
}

export const PARTICLE_PRESETS: ParticlePreset[] = [
  { id: "dust", name: "Dust", note: "Slow motes in the light" },
  { id: "sparks", name: "Sparks", note: "A quick graphite shower" },
  { id: "orbit", name: "Orbit", note: "A loose ring of ink" },
  { id: "confetti", name: "Confetti", note: "Cut-paper celebration" },
];

export interface PatternPreset {
  id: string;
  name: string;
  note: string;
  className: string;
}

export const PATTERN_PRESETS: PatternPreset[] = [
  { id: "jigsaw", name: "Jigsaw", note: "Interlocking arcs", className: "pattern-jigsaw" },
  { id: "topography", name: "Topography", note: "Contour lines", className: "pattern-topography" },
  { id: "circuit", name: "Circuit", note: "A board trace", className: "pattern-circuit" },
  { id: "diagonal", name: "Diagonal", note: "Cut paper stripes", className: "pattern-diagonal" },
  { id: "plus", name: "Plus", note: "A quiet cross grid", className: "pattern-plus" },
  { id: "rain", name: "Rain", note: "Short vertical marks", className: "pattern-rain" },
  { id: "bubbles", name: "Bubbles", note: "A loose foam", className: "pattern-bubbles" },
  { id: "aztec", name: "Aztec", note: "A stepped border", className: "pattern-aztec" },
];

export const MONO_COLORS = ["#fafaf7", "#e7e6e1", "#d8d7d1", "#8a8983", "#5a5a56", "#111111"];
export const ADD_COLORS = MONO_COLORS;
export const SHADER_COLORS = ["#111111", "#5a5a56", "#8a8983", "#d8d7d1", "#fafaf7"];
