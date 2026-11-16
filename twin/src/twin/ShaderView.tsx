import { Component, memo, type CSSProperties, type ReactNode } from "react";
import {
  ColorPanels,
  DotOrbit,
  FlutedGlass,
  GemSmoke,
  GrainGradient,
  HalftoneDots,
  ImageDithering,
  LiquidMetal,
  MeshGradient,
  PaperTexture,
  Warp,
} from "@paper-design/shaders-react";
import type { TwinEffect } from "./types";
import { ADD_COLORS, PATTERN_PRESETS, SHADER_COLORS } from "./presets";
import { usePageVisible } from "./usePageVisible";
import { useReducedMotion } from "./useReducedMotion";

const imageData = `data:image/svg+xml,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='640' height='360'><rect width='640' height='360' fill='#111111'/><circle cx='180' cy='180' r='120' fill='#fafaf7'/><path d='M400 70l110 220H290z' fill='#5a5a56'/></svg>",
)}`;

function shaderPalette(color: string | undefined): string[] {
  const selected = color && SHADER_COLORS.includes(color) ? color : SHADER_COLORS[0];
  return [selected, ...SHADER_COLORS.filter((value) => value !== selected)];
}

type ParticleEffectPreset = {
  id: string;
  count: number;
  size: number;
  color: string;
  opacity: number;
  shape: "dot" | "square" | "streak";
  layout: "scatter" | "orbit" | "trail";
};

const PARTICLE_EFFECTS: Record<string, ParticleEffectPreset> = {
  dust: { id: "dust", count: 80, size: 3, color: "#5a5a56", opacity: 0.68, shape: "dot", layout: "scatter" },
  sparks: { id: "sparks", count: 64, size: 2, color: "#111111", opacity: 0.84, shape: "streak", layout: "trail" },
  orbit: { id: "orbit", count: 48, size: 3, color: "#111111", opacity: 0.84, shape: "dot", layout: "orbit" },
  confetti: { id: "confetti", count: 42, size: 5, color: "#5a5a56", opacity: 0.82, shape: "square", layout: "scatter" },
};

const PATTERN_SIZES: Record<string, [number, number]> = {
  jigsaw: [28, 24],
  topography: [90, 72],
  circuit: [38, 38],
  diagonal: [22, 22],
  plus: [22, 22],
  rain: [38, 52],
  bubbles: [46, 42],
  aztec: [24, 24],
};

class ShaderErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? <ShaderFallback label="Shader unavailable" /> : this.props.children;
  }
}

let webglSupport: boolean | null = null;

export function canUseWebGL(): boolean {
  if (webglSupport !== null) return webglSupport;
  if (typeof window === "undefined" || typeof document === "undefined") return false;
  try {
    const canvas = document.createElement("canvas");
    const context = (canvas.getContext("webgl") || canvas.getContext("experimental-webgl")) as WebGLRenderingContext | null;
    webglSupport = Boolean(context);
    context?.getExtension("WEBGL_lose_context")?.loseContext();
    return webglSupport;
  } catch {
    webglSupport = false;
    return false;
  }
}

export function ShaderFallback({ label = "Paper texture" }: { label?: string }) {
  return (
    <div className="shader-fallback" data-testid="shader-fallback" aria-label={label}>
      <span className="shader-fallback-orbit" />
      <span className="shader-fallback-stamp">{label}</span>
    </div>
  );
}

export function ShaderView({
  shader,
  controls = {},
  className,
  style,
}: {
  shader: string;
  controls?: Record<string, number | string>;
  className?: string;
  style?: CSSProperties;
}) {
  const pageVisible = usePageVisible();
  const reduceMotion = useReducedMotion();
  if (!canUseWebGL()) return <ShaderFallback label="Paper shader fallback" />;
  const common = { className, style, scale: Number(controls.scale ?? 1) };
  const colors = shaderPalette(typeof controls.color === "string" ? controls.color : undefined);
  const speed = pageVisible && !reduceMotion ? Number(controls.speed ?? 0.5) : 0;
  const grain = Number(controls.grain ?? 0.2);
  const paper = String(controls.paper ?? "#fafaf7");
  const hasPaperControl = controls.paper !== undefined;

  let content: ReactNode;
  switch (shader) {
    case "MeshGradient":
      content = <MeshGradient {...common} colors={colors} speed={speed} distortion={Number(controls.distortion ?? 0.35)} swirl={Number(controls.swirl ?? 0.2)} grainOverlay={grain} />;
      break;
    case "GrainGradient":
      content = <GrainGradient {...common} colors={colors} colorBack={paper} speed={speed} intensity={Number(controls.distortion ?? 0.4)} noise={grain} shape="wave" />;
      break;
    case "Warp":
      content = <Warp {...common} colors={colors} speed={speed} distortion={Number(controls.distortion ?? 0.4)} swirl={Number(controls.swirl ?? 0.2)} shape="stripes" />;
      break;
    case "DotOrbit":
      content = <DotOrbit {...common} colors={colors} speed={speed} size={0.22} spreading={0.7} />;
      break;
    case "ColorPanels":
      content = <ColorPanels {...common} colors={colors} colorBack={paper} speed={speed} density={2.2} angle1={0.3} angle2={-0.2} gradient={0.7} />;
      break;
    case "LiquidMetal":
      content = <LiquidMetal {...common} colorBack={paper} colorTint={colors[0]} speed={speed} repetition={4} contour={0.4} softness={0.4} />;
      break;
    case "GemSmoke":
      content = <GemSmoke {...common} colors={colors} colorBack={paper} speed={speed} innerGlow={0.7} outerGlow={0.4} size={0.8} />;
      break;
    case "FlutedGlass":
      content = <FlutedGlass {...common} colorBack={paper} colorShadow="#111111" colorHighlight={paper} speed={speed} size={0.4} distortion={Number(controls.roughness ?? 0.45)} />;
      break;
    case "ImageDithering":
      content = <ImageDithering {...common} image={imageData} colorFront="#111111" colorBack={paper} colorHighlight="#5a5a56" size={3} type="4x4" />;
      break;
    case "HalftoneDots":
      content = <HalftoneDots {...common} image={imageData} colorFront="#111111" colorBack={paper} size={0.35} radius={0.8} contrast={Number(controls.roughness ?? 0.6)} />;
      break;
    case "PaperTexture":
    default:
      content = (
        <PaperTexture
          {...common}
          colorBack={paper}
          colorPaper={hasPaperControl ? paper : "#f2f1ed"}
          colorShadow={hasPaperControl ? "#8a8983" : "#cfcec8"}
          speed={pageVisible && !reduceMotion ? Number(controls.speed ?? 0) : 0}
          roughness={Number(controls.roughness ?? grain)}
          fiber={hasPaperControl ? 0.34 : 0.16}
          crumples={hasPaperControl ? 0.18 : 0.1}
          folds={hasPaperControl ? 0.14 : 0.08}
        />
      );
      break;
  }

  return (
    <ShaderErrorBoundary>
      <div className={`shader-host ${className ?? ""}`} style={style} data-shader={shader} data-page-visible={pageVisible} data-reduced-motion={reduceMotion}>
        {content}
      </div>
    </ShaderErrorBoundary>
  );
}

export function PatternSurface({
  pattern,
  opacity = 0.8,
  color = "#111111",
  scale = 1,
}: {
  pattern: string;
  opacity?: number;
  color?: string;
  scale?: number;
}) {
  const preset = PATTERN_PRESETS.find((item) => item.id === pattern) ?? PATTERN_PRESETS[0];
  const [width, height] = PATTERN_SIZES[preset.id] ?? [24, 24];
  return (
    <div
      className={`pattern-surface ${preset.className}`}
      style={{
        opacity,
        ["--pattern-ink" as string]: color,
        backgroundSize: `${width * scale}px ${height * scale}px`,
      }}
      aria-label={`${preset.name} pattern`}
    />
  );
}

function EffectSurfaceView({ effect }: { effect: TwinEffect | null }) {
  if (!effect) return <div className="effect-empty" />;
  if (effect.kind === "shader") return <ShaderView shader={effect.shader} controls={effect.controls} />;
  if (effect.kind === "solid") return <div className="effect-solid" style={{ background: effect.hex }} />;
  if (effect.kind === "pattern") {
    return (
      <PatternSurface
        pattern={effect.preset}
        opacity={Number(effect.controls.opacity ?? 0.8)}
        color={String(effect.controls.ink ?? "#111111")}
        scale={Number(effect.controls.scale ?? 1)}
      />
    );
  }
  if (effect.kind === "field") {
    const colors = effect.colors.length ? effect.colors : ["#fafaf7", "#e7e6e1", "#8a8983", "#111111"];
    return (
      <div
        className="effect-field"
        aria-label={`${effect.preset} field`}
        style={{
          background: [
            `radial-gradient(circle at 50% 26%, ${colors[0]} 0 24%, transparent 46%)`,
            `radial-gradient(circle at 77% 50%, ${colors[1]} 0 24%, transparent 46%)`,
            `radial-gradient(circle at 50% 74%, ${colors[2]} 0 24%, transparent 46%)`,
            `radial-gradient(circle at 23% 50%, ${colors[3]} 0 24%, transparent 46%)`,
            colors[0],
          ].join(", "),
        }}
      />
    );
  }
  if (effect.kind === "texture") {
    const opacity = Math.max(0, Math.min(1, Number(effect.controls.opacity ?? 1)));
    const scale = Math.max(0.5, Math.min(2.5, Number(effect.controls.scale ?? 1)));
    if (effect.preset === "Fur" && !effect.image) {
      const fluff = Number(effect.controls.fluff ?? 1);
      return (
        <div
          className="effect-fur"
          aria-label="Fur texture"
          style={{
            ["--fur-paper" as string]: String(effect.controls.paper ?? "#fafaf7"),
            opacity: Math.max(0.45, Math.min(1, 0.35 + fluff * 0.35)) * opacity,
            transform: `scale(${scale})`,
            transformOrigin: "center",
          }}
        />
      );
    }
    return (
      <div className="effect-texture" style={{ opacity }}>
        {effect.image ? <img src={effect.image} alt="" className="effect-texture-image" /> : null}
        <ShaderView shader={effect.preset} controls={effect.controls} />
      </div>
    );
  }
  const particle = PARTICLE_EFFECTS[effect.preset] ?? PARTICLE_EFFECTS.dust;
  const density = Math.max(0.3, Math.min(2, Number(effect.controls.density ?? 1)));
  const count = Math.round(particle.count * density);
  const color = String(effect.controls.colour ?? particle.color);
  const trail = Number(effect.controls.trail ?? 0) === 1;
  return (
    <div
      className={`effect-particles effect-particles-${particle.id} ${trail ? "is-trail" : ""}`}
      data-testid="particle-effect-surface"
      data-preset={particle.id}
      data-count={count}
      data-speed={String(effect.controls.speed ?? 1)}
      data-trail={trail ? "true" : "false"}
    >
      {Array.from({ length: count }).map((_, index) => {
        const angle = (index / count) * Math.PI * 2 + 0.4;
        const radius = 34 + (index % 4) * 3.5;
        const position = particle.layout === "orbit"
          ? { left: `${50 + Math.cos(angle) * radius}%`, top: `${50 + Math.sin(angle) * radius}%` }
          : particle.layout === "trail"
            ? { left: `${(index * 37) % 100}%`, top: `${(index * 53) % 100}%` }
            : { left: `${(index * 37) % 100}%`, top: `${(index * 53) % 100}%` };
        return (
          <span
            key={index}
            className={`particle-mark particle-mark-${particle.shape}`}
            style={{
              ...position,
              width: `${particle.size}px`,
              height: particle.shape === "streak" ? `${particle.size * 4}px` : `${particle.size}px`,
              background: color,
              opacity: particle.opacity,
              transform: particle.shape === "square" ? `rotate(${(index % 4) * 45}deg)` : particle.shape === "streak" ? `rotate(18deg)` : undefined,
            }}
          />
        );
      })}
    </div>
  );
}

export const EffectSurface = memo(EffectSurfaceView);

export const SHADER_COLORS_FALLBACK = ADD_COLORS;
