import { useState } from "react";
import type { PluginPanelProps } from "../twin/types";
import { useTwinStore } from "../twin/store";
import { ActionRow, PluginIntro, TargetSwitch } from "./ui";

// The native shelf is the deliberate colour exception in the neutral twin.
const TONES = [
  { id: "plum", name: "Plum", hex: "#5B2A4B" },
  { id: "rose", name: "Rose", hex: "#6D3558" },
  { id: "night", name: "Night", hex: "#1B1212" },
  { id: "paper", name: "Paper", hex: "#F5ECD8" },
  { id: "brown", name: "Brown", hex: "#8A3B00" },
];
const PRISMS = [
  { id: "mica-dawn", name: "Mica dawn", colors: ["#F3E9DA", "#C7A88B", "#6B5A4E", "#2C2723"] },
  { id: "sea-glass", name: "Sea glass", colors: ["#E7F4EF", "#9CD3C4", "#3A8A7A", "#123F3A"] },
  { id: "plum-smoke", name: "Plum smoke", colors: ["#F2E6EE", "#C89DBB", "#7B4C6D", "#2B1B2A"] },
  { id: "citrus-haze", name: "Citrus haze", colors: ["#FFF6D9", "#FFD37A", "#F08A4B", "#7A2E1F"] },
  { id: "cobalt-rain", name: "Cobalt rain", colors: ["#E8F1FA", "#9CBCE8", "#3767B1", "#14274D"] },
  { id: "olive-dusk", name: "Olive dusk", colors: ["#F0EEDC", "#C7C58F", "#6C7A4A", "#28301F"] },
  { id: "rose-quartz", name: "Rose quartz", colors: ["#FBEDEE", "#E7B7BC", "#B86B78", "#4A2430"] },
  { id: "ink-tide", name: "Ink tide", colors: ["#E4EEF0", "#8FB6C2", "#3C6C7D", "#0E2630"] },
];

function fieldBackground(colors: string[]): string {
  return [
    `radial-gradient(circle at 50% 26%, ${colors[0]} 0 24%, transparent 46%)`,
    `radial-gradient(circle at 77% 50%, ${colors[1]} 0 24%, transparent 46%)`,
    `radial-gradient(circle at 50% 74%, ${colors[2]} 0 24%, transparent 46%)`,
    `radial-gradient(circle at 23% 50%, ${colors[3]} 0 24%, transparent 46%)`,
    colors[0],
  ].join(", ");
}

export default function NativeParityPlugin({ target, setTarget, apply }: PluginPanelProps) {
  const store = useTwinStore();
  const tab = store.state.nativeTab;
  const setTab = store.setNativeTab;
  const [tone, setTone] = useState(TONES[0]);
  const [prism, setPrism] = useState(PRISMS[0]);

  return (
    <div className="plugin-body">
      <PluginIntro>
        The same Tone and Prism labels as the native shelf. This is a browser mirror, not a claim that the pixels are native. These native colours stay in colour as the reference exception.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="segmented" role="group" aria-label="Native plugin">
        <button type="button" aria-pressed={tab === "tone"} onClick={() => setTab("tone")}>Tone</button>
        <button type="button" aria-pressed={tab === "prism"} onClick={() => setTab("prism")}>Prism</button>
      </div>
      {tab === "tone" ? (
        <div className="tone-grid">
          {TONES.map((item) => (
            <button
              type="button"
              key={item.id}
              className={`tone-card ${tone.id === item.id ? "is-selected" : ""}`}
              aria-pressed={tone.id === item.id}
              onClick={() => setTone(item)}
            >
              <span style={{ background: item.hex }} />
              <strong>{item.name}</strong>
              <small>{item.hex}</small>
            </button>
          ))}
        </div>
      ) : (
        <div className="prism-list">
          {PRISMS.map((item) => (
            <button
              type="button"
              key={item.id}
              className={`prism-row ${prism.id === item.id ? "is-selected" : ""}`}
              aria-pressed={prism.id === item.id}
              aria-label={`Apply ${item.name} gradient`}
              onClick={() => setPrism(item)}
            >
              <span className="native-field-preview" style={{ background: fieldBackground(item.colors) }} aria-hidden="true" />
              <span>
                <strong>{item.name}</strong>
                <small>{item.colors.join(" · ")}</small>
              </span>
            </button>
          ))}
        </div>
      )}
      <ActionRow>
        <button
          type="button"
          className="button primary"
          onClick={() => apply(tab === "tone" ? { kind: "solid", hex: tone.hex } : { kind: "field", preset: prism.name, colors: prism.colors })}
        >
          Apply {tab === "tone" ? tone.name : prism.name} to {target}
        </button>
      </ActionRow>
    </div>
  );
}
