import { useState } from "react";
import { Fur } from "feral-fur";
import "feral-fur/fur.css";
import { TEXTURE_PRESETS } from "../editor-core/presets";
import { ShaderView } from "../editor-core/ShaderView";
import type { PluginPanelProps, EditorEffect } from "../editor-core/types";
import { ActionRow, PluginIntro, RangeField, Swatches, TargetSwitch } from "./ui";

const PAPER_COLORS = ["#fafaf7", "#e7e6e1", "#d8d7d1"];
const isTest = import.meta.env.MODE === "test";

export default function TextureLabPlugin({ target, setTarget, apply }: PluginPanelProps) {
  const [selected, setSelected] = useState(TEXTURE_PRESETS[0]);
  const [paper, setPaper] = useState(PAPER_COLORS[0]);
  const [roughness, setRoughness] = useState(0.4);
  const [fluff, setFluff] = useState(1);
  const [scale, setScale] = useState(1);
  const [opacity, setOpacity] = useState(100);

  const effect = (preset = selected): EditorEffect => ({
    kind: "texture",
    preset: preset.shader,
    controls: { roughness, paper, fluff, scale, opacity: opacity / 100 },
  });

  const choose = (preset: (typeof TEXTURE_PRESETS)[number]) => {
    setSelected(preset);
    apply(effect(preset));
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        Paper, ribbed glass, a halftone print, and a coat of fur. These are material studies, not filters over a photograph.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="texture-feature" data-testid="texture-feature">
        {selected.id === "fur" ? (
          isTest ? (
            <div className="fur-fallback" data-testid="fur-preview">
              BMO
            </div>
          ) : (
            <Fur text="BMO" color={paper} fluff={fluff} density={0.9} mess={0.45} style={{ width: "100%", height: "100%", opacity: opacity / 100, transform: `scale(${scale})`, transformOrigin: "center" }} />
          )
        ) : (
          <ShaderView shader={selected.shader} controls={{ roughness, paper, scale }} style={{ opacity: opacity / 100 }} />
        )}
        <span className="texture-stamp">{selected.name}</span>
      </div>
      <div className="preset-grid texture-grid">
        {TEXTURE_PRESETS.map((preset) => (
          <button
            type="button"
            key={preset.id}
            className={`texture-card ${selected.id === preset.id ? "is-selected" : ""}`}
            aria-pressed={selected.id === preset.id}
            onClick={() => choose(preset)}
            data-testid={`texture-preset-${preset.id}`}
          >
            <span>{preset.name}</span>
            <small>{preset.note}</small>
          </button>
        ))}
      </div>
      <div className="control-stack">
        <Swatches colors={PAPER_COLORS} value={paper} onChange={setPaper} label="Paper" />
        <RangeField label="Scale" value={scale} min={0.5} max={2.5} step={0.05} onChange={setScale} />
        <RangeField label="Opacity" value={opacity} min={10} max={100} onChange={setOpacity} suffix="%" />
        <RangeField label="Roughness" value={roughness} min={0} max={1} step={0.05} onChange={setRoughness} />
        <RangeField label="Fur length" value={fluff} min={0.4} max={2} step={0.1} onChange={setFluff} />
      </div>
      <ActionRow>
        <button type="button" className="button primary" onClick={() => apply(effect())}>
          Apply to {target}
        </button>
        <button type="button" className="button quiet" onClick={() => apply({ kind: "solid", hex: paper })}>
          Clear to paper
        </button>
      </ActionRow>
    </div>
  );
}
