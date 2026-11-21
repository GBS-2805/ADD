import { useEffect, useRef, useState } from "react";
import rough from "roughjs";
import { PATTERN_PRESETS } from "../editor-core/presets";
import { PatternSurface } from "../editor-core/ShaderView";
import type { PluginPanelProps } from "../editor-core/types";
import { ActionRow, PluginIntro, RangeField, Swatches, TargetSwitch } from "./ui";

const INKS = ["#111111", "#5a5a56", "#8a8983", "#d8d7d1"];

function RoughFrame({ active }: { active: boolean }) {
  const ref = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = ref.current;
    if (!svg) return;
    svg.replaceChildren();
    if (!active) return;
    const path = rough.svg(svg).rectangle(3, 3, 94, 64, {
      stroke: "#111111",
      strokeWidth: 1.4,
      roughness: 1.8,
      fill: "none",
    });
    svg.appendChild(path);
    return () => svg.replaceChildren();
  }, [active]);

  return <svg ref={ref} className="rough-frame" viewBox="0 0 100 70" aria-hidden="true" />;
}

export default function PatternLibraryPlugin({ target, setTarget, apply }: PluginPanelProps) {
  const [selected, setSelected] = useState(PATTERN_PRESETS[0]);
  const [roughMode, setRoughMode] = useState(false);
  const [opacity, setOpacity] = useState(0.78);
  const [ink, setInk] = useState(INKS[0]);
  const [scale, setScale] = useState(1);

  const choose = (preset: (typeof PATTERN_PRESETS)[number]) => {
    setSelected(preset);
    apply({ kind: "pattern", preset: preset.id, controls: { opacity, ink, scale, rough: roughMode ? 1 : 0 } });
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        Eight repeatable grounds, printed in one ink. Rough mode gives the sample edge a pencil wobble.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="pattern-feature" data-testid="pattern-feature">
        <PatternSurface pattern={selected.id} opacity={opacity} color={ink} scale={scale} />
        {roughMode ? <RoughFrame active /> : null}
        <span className="pattern-stamp">{selected.name}</span>
      </div>
      <div className="pattern-grid">
        {PATTERN_PRESETS.map((preset) => (
          <button
            type="button"
            key={preset.id}
            className={`pattern-card ${selected.id === preset.id ? "is-selected" : ""}`}
            aria-pressed={selected.id === preset.id}
            onClick={() => choose(preset)}
            data-testid={`pattern-preset-${preset.id}`}
          >
            <span className={`pattern-surface ${preset.className}`} style={{ ["--pattern-ink" as string]: ink }} aria-hidden="true" />
            <small>{preset.name}</small>
          </button>
        ))}
      </div>
      <div className="control-stack">
        <Swatches colors={INKS} value={ink} onChange={setInk} label="Ink" />
        <RangeField label="Scale" value={scale} min={0.5} max={2.5} step={0.05} onChange={setScale} />
        <RangeField label="Opacity" value={Math.round(opacity * 100)} min={10} max={100} onChange={(value) => setOpacity(value / 100)} suffix="%" />
        <label className="toggle-row">
          <span>
            <strong>Rough sample edge</strong>
            <small>Rough.js outline on the preview only</small>
          </span>
          <input type="checkbox" checked={roughMode} onChange={(event) => setRoughMode(event.target.checked)} />
        </label>
      </div>
      <ActionRow>
        <button
          type="button"
          className="button primary"
          onClick={() => apply({ kind: "pattern", preset: selected.id, controls: { opacity, ink, scale, rough: roughMode ? 1 : 0 } })}
        >
          Apply to {target}
        </button>
      </ActionRow>
    </div>
  );
}
