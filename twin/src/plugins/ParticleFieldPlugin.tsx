import { useState } from "react";
import Particles, { ParticlesProvider } from "@tsparticles/react";
import { loadSlim } from "@tsparticles/slim";
import type { ISourceOptions } from "@tsparticles/engine";
import { PARTICLE_PRESETS } from "../twin/presets";
import type { PluginPanelProps } from "../twin/types";
import { usePageVisible } from "../twin/usePageVisible";
import { useReducedMotion } from "../twin/useReducedMotion";
import { ActionRow, PluginIntro, RangeField, Swatches, TargetSwitch } from "./ui";

const PARTICLE_COLORS = ["#111111", "#5a5a56", "#8a8983", "#d8d7d1"];

const isTest = import.meta.env.MODE === "test";

const PRESET_OPTIONS: Record<string, { count: number; speed: number; color: string; size: number; trail: boolean }> = {
  dust: { count: 38, speed: 0.4, color: "#8a8983", size: 2, trail: false },
  sparks: { count: 58, speed: 3.4, color: "#5a5a56", size: 1.5, trail: true },
  orbit: { count: 26, speed: 1.1, color: "#111111", size: 3, trail: false },
  confetti: { count: 46, speed: 2.1, color: "#d8d7d1", size: 4, trail: true },
};

export default function ParticleFieldPlugin({ target, setTarget, apply, toast }: PluginPanelProps) {
  const [selected, setSelected] = useState(PARTICLE_PRESETS[0]);
  const [paused, setPaused] = useState(false);
  const [density, setDensity] = useState(1);
  const [speed, setSpeed] = useState(1);
  const [colour, setColour] = useState(PARTICLE_COLORS[0]);
  const [trail, setTrail] = useState(false);
  const pageVisible = usePageVisible();
  const reduceMotion = useReducedMotion();
  const settings = PRESET_OPTIONS[selected.id];

  const options: ISourceOptions = {
    fullScreen: { enable: false },
    fpsLimit: 60,
    detectRetina: true,
    particles: {
      number: { value: Math.round(settings.count * density) },
      color: { value: colour },
      shape: { type: selected.id === "confetti" ? "square" : "circle" },
      size: { value: { min: 1, max: settings.size + (trail ? 1 : 0) } },
      opacity: { value: { min: trail ? 0.35 : 0.2, max: 0.8 } },
      move: {
        enable: !paused && pageVisible && !reduceMotion,
        speed,
        direction: "none",
        outModes: { default: "bounce" },
      },
      links: { enable: false },
    },
  };

  const choose = (preset: (typeof PARTICLE_PRESETS)[number]) => {
    setSelected(preset);
    apply({ kind: "particles", preset: preset.id, controls: { density, speed, colour, trail: trail ? 1 : 0 } });
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        A field of small marks for the edge of a poster. Pause it when it is not doing any work.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="particle-stage" data-testid="particle-preview" data-page-visible={pageVisible} data-reduced-motion={reduceMotion}>
        {isTest ? (
          <div className="particle-fallback" aria-hidden="true">
            {Array.from({ length: 22 }).map((_, index) => <span key={index} style={{ ["--i" as string]: index }} />)}
          </div>
        ) : (
          <ParticlesProvider init={loadSlim}>
            <Particles id="twin-particles" options={options} />
          </ParticlesProvider>
        )}
        <span className="particle-stamp">{paused ? "PAUSED" : selected.name}</span>
      </div>
      <div className="preset-grid particle-grid">
        {PARTICLE_PRESETS.map((preset) => (
          <button
            type="button"
            key={preset.id}
            className={`particle-card ${selected.id === preset.id ? "is-selected" : ""}`}
            aria-pressed={selected.id === preset.id}
            onClick={() => choose(preset)}
            data-testid={`particle-preset-${preset.id}`}
          >
            <strong>{preset.name}</strong>
            <small>{preset.note}</small>
          </button>
        ))}
      </div>
      <div className="control-stack">
        <RangeField label="Density" value={density} min={0.3} max={2} step={0.1} onChange={setDensity} />
        <RangeField label="Speed" value={speed} min={0.2} max={4} step={0.1} onChange={setSpeed} />
        <Swatches colors={PARTICLE_COLORS} value={colour} onChange={setColour} label="Colour" />
        <label className="toggle-row">
          <span>
            <strong>Trail</strong>
            <small>Keep a longer mark behind each particle</small>
          </span>
          <input type="checkbox" checked={trail} onChange={(event) => setTrail(event.target.checked)} />
        </label>
      </div>
      <ActionRow>
        <button type="button" className="button primary" onClick={() => apply({ kind: "particles", preset: selected.id, controls: { density, speed, colour, trail: trail ? 1 : 0 } })}>
          Apply to {target}
        </button>
        <button
          type="button"
          className="button quiet"
          aria-pressed={paused}
          onClick={() => {
            setPaused((value) => !value);
            toast(paused ? "Particles resumed." : "Particles paused.");
          }}
        >
          {paused ? "Resume" : "Pause"}
        </button>
      </ActionRow>
    </div>
  );
}
