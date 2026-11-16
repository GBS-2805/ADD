import { useRef, useState } from "react";
import { ArrowDown, ArrowUp, Eye, EyeOff, Plus, Trash2 } from "lucide-react";
import { BLEND_MODES, type BlendMode, type PluginPanelProps } from "../twin/types";
import { useTwinStore } from "../twin/store";
import { ActionRow, EmptyState, PluginIntro, RangeField } from "./ui";
import { fromLegacy, toLegacy } from "../shell/legacy";

const BLEND_LABELS: Record<BlendMode, string> = {
  normal: "Normal",
  multiply: "Multiply",
  screen: "Screen",
  overlay: "Overlay",
  darken: "Darken",
  lighten: "Lighten",
  "color-dodge": "Color dodge",
  "color-burn": "Color burn",
  "hard-light": "Hard light",
  "soft-light": "Soft light",
  difference: "Difference",
  exclusion: "Exclusion",
  hue: "Hue",
  saturation: "Saturation",
  color: "Color",
  luminosity: "Luminosity",
};

export default function OverlayLabPlugin({
  doc,
  toast,
}: PluginPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const store = useTwinStore();
  const selected = doc.layers.find((layer) => layer.id === doc.selectedLayerIds[0]);
  const isImage = selected?.kind === "image";

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    try {
      await store.addImageLayer(file);
    } catch (error) {
      if (!(error instanceof Error) || error.message === "PNG only") {
        toast("The PNG could not be added.");
      }
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        A real PNG layer stack. Add a transparent file, put it in order, then blend it into the sheet. No PSD parser: export a PSD to PNG first.
      </PluginIntro>
      <div className="overlay-toolbar">
        <button type="button" className="button primary" onClick={() => inputRef.current?.click()} disabled={busy}>
          <Plus size={17} /> {busy ? "Reading…" : "Add PNG"}
        </button>
        <span className="layer-count">{doc.layers.filter((layer) => layer.kind === "image").length} / 12 PNG</span>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/png"
        hidden
        data-testid="overlay-import-input"
        onChange={(event) => void handleFile(event.target.files?.[0])}
      />
      <div className="layer-list" role="list" aria-label="PNG layers">
        {doc.layers.length === 0 ? (
          <EmptyState title="The stack is empty" body="Add a PNG to begin a composition." />
        ) : (
          [...doc.layers].reverse().map((layer) => (
            <div key={layer.id} className={`layer-row ${doc.selectedLayerIds.includes(layer.id) ? "is-selected" : ""}`} role="listitem">
              <button type="button" className="layer-select" onClick={() => store.selectLayer(layer.id)}>
                <span className={`layer-thumb ${layer.kind}`}>
                  {layer.image ? <img src={layer.image.src} alt="" /> : layer.kind === "text" ? "Aa" : "◇"}
                </span>
                <span>
                  <strong>{layer.name}</strong>
                  <small>{layer.kind === "image" ? `${layer.image?.width ?? 0} × ${layer.image?.height ?? 0}` : layer.kind}</small>
                </span>
              </button>
              <div className="layer-row-actions">
                <button type="button" className="icon-button" aria-label={`${layer.visible ? "Hide" : "Show"} ${layer.name}`} onClick={() => store.setLayerVisibility(layer.id, !layer.visible)}>
                  {layer.visible ? <Eye size={15} /> : <EyeOff size={15} />}
                </button>
                <button type="button" className="icon-button" aria-label={`Move ${layer.name} up`} onClick={() => store.moveLayer(layer.id, "up")}>
                  <ArrowUp size={15} />
                </button>
                <button type="button" className="icon-button" aria-label={`Move ${layer.name} down`} onClick={() => store.moveLayer(layer.id, "down")}>
                  <ArrowDown size={15} />
                </button>
                {layer.kind === "image" ? (
                  <button type="button" className="icon-button danger" aria-label={`Remove ${layer.name}`} onClick={() => store.removeLayer(layer.id)}>
                    <Trash2 size={15} />
                  </button>
                ) : null}
              </div>
            </div>
          ))
        )}
      </div>
      {selected && isImage ? (
        <div className="overlay-controls">
          <label className="field-row">
            <span>Blend mode</span>
            <select value={selected.blend} onChange={(event) => store.setLayerBlend(selected.id, event.target.value as BlendMode)} aria-label="Blend mode">
              {BLEND_MODES.map((mode) => <option key={mode} value={mode}>{BLEND_LABELS[mode]}</option>)}
            </select>
          </label>
          <RangeField label="Opacity" value={Math.round(selected.opacity * 100)} min={0} max={100} onChange={(value) => store.setLayerOpacity(selected.id, value / 100)} suffix="%" />
          <div className="pair-grid">
            <RangeField label="X" value={Math.round(toLegacy(selected.transform.x))} min={-180} max={180} onChange={(value) => store.setLayerTransform(selected.id, { x: fromLegacy(value) })} />
            <RangeField label="Y" value={Math.round(toLegacy(selected.transform.y))} min={-180} max={180} onChange={(value) => store.setLayerTransform(selected.id, { y: fromLegacy(value) })} />
            <RangeField label="Scale" value={selected.transform.scaleX} min={0.1} max={4} step={0.05} onChange={(value) => store.setLayerTransform(selected.id, { scaleX: value, scaleY: value })} />
            <RangeField label="Rotation" value={Math.round(selected.transform.rotation)} min={-180} max={180} onChange={(value) => store.setLayerTransform(selected.id, { rotation: value })} suffix="°" />
          </div>
          <ActionRow>
            <button type="button" className="button primary" onClick={() => toast("The layer is already live on the artboard.")}>
              Selected layer
            </button>
            <button type="button" className="button quiet" onClick={() => store.removeLayer(selected.id)}>
              Remove PNG
            </button>
          </ActionRow>
        </div>
      ) : (
        <p className="plugin-note">Select a PNG layer and drag it on the artboard, or edit its blend, opacity and transform. Sample type and shapes stay in the document.</p>
      )}
    </div>
  );
}
