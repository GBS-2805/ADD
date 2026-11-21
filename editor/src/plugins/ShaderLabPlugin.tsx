import { useState } from "react";
import { SHADER_COLORS, SHADER_PRESETS } from "../editor-core/presets";
import { ShaderView } from "../editor-core/ShaderView";
import type { PluginPanelProps, EditorEffect } from "../editor-core/types";
import { ActionRow, PluginIntro, RangeField, Swatches, TargetSwitch } from "./ui";

export default function ShaderLabPlugin({ target, setTarget, apply }: PluginPanelProps) {
  const [selected, setSelected] = useState(SHADER_PRESETS[0]);
  const [speed, setSpeed] = useState(0.5);
  const [distortion, setDistortion] = useState(0.35);
  const [swirl, setSwirl] = useState(0.2);
  const [grain, setGrain] = useState(0.2);
  const [colour, setColour] = useState(SHADER_COLORS[0]);

  const effect = (preset = selected): EditorEffect => ({
    kind: "shader",
    shader: preset.shader,
    preset: preset.name,
    controls: { speed, distortion, swirl, grain, color: colour },
  });

  const choose = (preset: (typeof SHADER_PRESETS)[number]) => {
    setSelected(preset);
    apply(effect(preset));
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        A live tray of GPU papers. Pick a ground, tune the pressure, and send it to the artboard or the selected layer.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="shader-feature" data-testid="shader-feature">
        <ShaderView shader={selected.shader} controls={{ speed, distortion, swirl, grain, color: colour }} />
        <span className="texture-stamp">{selected.name}</span>
      </div>
      <div className="preset-grid shader-grid">
        {SHADER_PRESETS.map((preset) => (
          <button
            type="button"
            key={preset.id}
            className={`preset-card ${selected.id === preset.id ? "is-selected" : ""}`}
            aria-pressed={selected.id === preset.id}
            onClick={() => choose(preset)}
            data-testid={`shader-preset-${preset.id}`}
          >
            <span className={`preset-thumb shader-thumb-${preset.id}`} aria-hidden="true" />
            <span className="preset-copy">
              <strong>{preset.name}</strong>
              <small>{preset.note}</small>
            </span>
          </button>
        ))}
      </div>
      <div className="control-stack">
        <RangeField label="Speed" value={speed} min={0} max={1} step={0.05} onChange={setSpeed} />
        <RangeField label="Distortion" value={distortion} min={0} max={1} step={0.05} onChange={setDistortion} />
        <RangeField label="Swirl" value={swirl} min={0} max={1} step={0.05} onChange={setSwirl} />
        <RangeField label="Grain" value={grain} min={0} max={1} step={0.05} onChange={setGrain} />
        <Swatches colors={SHADER_COLORS} value={colour} onChange={setColour} label="Colour" />
      </div>
      <ActionRow>
        <button type="button" className="button primary" onClick={() => apply(effect())}>
          Apply to {target}
        </button>
        <button type="button" className="button quiet" onClick={() => apply({ kind: "solid", hex: "#fafaf7" })}>
          Clear to paper
        </button>
      </ActionRow>
    </div>
  );
}
