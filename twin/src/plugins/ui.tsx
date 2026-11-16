import type { ReactNode } from "react";
import type { EffectTarget } from "../twin/types";

export function PluginIntro({ children }: { children: ReactNode }) {
  return <p className="plugin-intro">{children}</p>;
}

export function TargetSwitch({
  value,
  onChange,
}: {
  value: EffectTarget;
  onChange: (target: EffectTarget) => void;
}) {
  return (
    <div className="segmented" role="group" aria-label="Apply to">
      <button type="button" aria-pressed={value === "artboard"} onClick={() => onChange("artboard")}>
        Artboard
      </button>
      <button type="button" aria-pressed={value === "layer"} onClick={() => onChange("layer")}>
        Layer
      </button>
    </div>
  );
}

export function RangeField({
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
  suffix,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (value: number) => void;
  suffix?: string;
}) {
  return (
    <label className="range-field">
      <span>
        {label}
        <strong>
          {value}
          {suffix}
        </strong>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      />
    </label>
  );
}

export function ActionRow({ children }: { children: ReactNode }) {
  return <div className="action-row">{children}</div>;
}

export function Swatches({
  colors,
  value,
  onChange,
  label = "Ink",
}: {
  colors: string[];
  value: string;
  onChange: (color: string) => void;
  label?: string;
}) {
  return (
    <fieldset className="swatch-field">
      <legend>{label}</legend>
      <div className="swatch-row">
        {colors.map((color) => (
          <button
            key={color}
            type="button"
            className="swatch"
            style={{ background: color }}
            aria-label={`${label} ${color}`}
            aria-pressed={value === color}
            onClick={() => onChange(color)}
          />
        ))}
      </div>
    </fieldset>
  );
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="empty-state">
      <span className="empty-mark">+</span>
      <strong>{title}</strong>
      <p>{body}</p>
    </div>
  );
}
