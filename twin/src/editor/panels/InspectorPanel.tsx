import { useTwinStore } from "../../twin/store";
import { layerBounds, selectionBounds } from "../model/geometry";
import type { TwinEffect, TwinLayer } from "../../twin/types";
import { BlendSelect, FillPicker, FontSelect } from "./controls";
import { AlignSection } from "./AlignSection";
import { NumericField } from "./NumericField";
import { TransformSection } from "./TransformSection";

const round = (value: number) => Math.round(value * 100) / 100;

function backgroundLabel(effect: TwinEffect | null | undefined): string {
  if (!effect) return "None";
  if (effect.kind === "solid") return effect.hex;
  if (effect.kind === "field") return "Field";
  if (effect.kind === "unsupported") return `${effect.originalKind} (unsupported)`;
  return effect.preset;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="ed-inspector-section" aria-label={title}>
      <div className="ed-inspector-section-head"><span>{title}</span></div>
      {children}
    </section>
  );
}

function DocumentInspector() {
  const store = useTwinStore();
  const { doc } = store.state;
  return (
    <section className="ed-panel-body ed-inspector-panel" data-testid="inspector-panel" aria-label="Inspector">
      <div className="ed-panel-heading"><span>Artboard</span><span>Document</span></div>
      <div className="ed-document-readout">
        <span>Canvas</span><strong>765 × 1000</strong>
        <span>Background</span><strong>{backgroundLabel(doc.background)}</strong>
        <span>Export</span><strong>1530 × 2000</strong>
      </div>
      <button type="button" className="ed-inspector-action" onClick={store.openPresentation}>Preview</button>
      <p className="ed-panel-empty">Select a layer to inspect its transform, alignment and appearance.</p>
    </section>
  );
}

function SingleInspector({ layer }: { layer: TwinLayer }) {
  const store = useTwinStore();
  const bounds = layerBounds(layer);
  return (
    <section className="ed-panel-body ed-inspector-panel" data-testid="inspector-panel" aria-label="Inspector panel">
      <div className="ed-panel-heading">
        <span>{layer.name}</span>
        <span>{layer.kind}</span>
      </div>
      <TransformSection layer={layer} />
      <AlignSection layers={store.state.doc.layers} selectedIds={[layer.id]} />
      <Section title="Appearance">
        <div className="ed-inspector-inline">
          <NumericField
            label="Opacity"
            value={Math.round(layer.opacity * 100)}
            min={0}
            max={100}
            suffix="%"
            onCommit={(value) => store.setLayerOpacity(layer.id, value / 100)}
          />
          <span className="ed-bounds-readout">{round(bounds.width)} × {round(bounds.height)} bounds</span>
        </div>
        <BlendSelect layer={layer} />
        <button type="button" className="ed-inspector-action" onClick={() => store.setRightTab("effects")}>Open effects</button>
      </Section>
      {layer.kind === "text" && layer.text ? (
        <Section title="Type">
          <FontSelect layer={layer} />
          <label className="ed-textarea-field">
            <span>Text content</span>
            <textarea
              rows={3}
              value={layer.text.content}
              onChange={(event) => store.setLayerText(layer.id, event.target.value)}
            />
          </label>
        </Section>
      ) : null}
      {layer.kind === "image" && layer.image ? (
        <Section title="Image">
          <div className="ed-document-readout">
            <span>Source</span><strong>{layer.image.width} × {layer.image.height} px</strong>
          </div>
        </Section>
      ) : null}
      {layer.kind === "shape" ? (
        <Section title="Shape">
          <p className="ed-panel-empty">{layer.shape?.variant ?? "star"} shape · every edit is undoable.</p>
        </Section>
      ) : null}
      {layer.effect ? (
        <Section title="Effect">
          <div className="ed-document-readout">
            <span>Preset</span><strong>{backgroundLabel(layer.effect)}</strong>
          </div>
          {layer.effect.kind === "unsupported" ? (
            <p className="ed-panel-empty">
              This effect comes from a newer library than this build. It is kept exactly as
              saved and will draw when this build learns it.
            </p>
          ) : null}
        </Section>
      ) : null}
      {layer.kind !== "image" ? (
        <Section title="Fill"><FillPicker layer={layer} /></Section>
      ) : null}
    </section>
  );
}

function MultiInspector({ layers }: { layers: TwinLayer[] }) {
  const store = useTwinStore();
  const bounds = selectionBounds(layers, store.state.doc.selectedLayerIds);
  const opacities = new Set(layers.map((layer) => layer.opacity));
  const blends = new Set(layers.map((layer) => layer.blend));
  return (
    <section className="ed-panel-body ed-inspector-panel" data-testid="inspector-panel" aria-label="Inspector panel">
      <div className="ed-panel-heading"><span>{layers.length} layers</span><span>Selection</span></div>
      {bounds ? (
        <div className="ed-document-readout">
          <span>Bounds</span><strong>{round(bounds.width)} × {round(bounds.height)}</strong>
          <span>Position</span><strong>{round(bounds.x)}, {round(bounds.y)}</strong>
        </div>
      ) : null}
      <AlignSection layers={store.state.doc.layers} selectedIds={store.state.doc.selectedLayerIds} />
      <Section title="Appearance">
        <div className="ed-inspector-inline">
          <span>Opacity</span>
          <strong>{opacities.size === 1 ? `${Math.round([...opacities][0] * 100)}%` : "Mixed"}</strong>
        </div>
        <div className="ed-inspector-inline">
          <span>Blend</span>
          <strong>{blends.size === 1 ? [...blends][0] : "Mixed"}</strong>
        </div>
      </Section>
      <p className="ed-panel-empty">Select one layer to edit its full transform.</p>
    </section>
  );
}

export function InspectorPanel() {
  const store = useTwinStore();
  const { doc } = store.state;
  const selected = doc.layers.filter((layer) => doc.selectedLayerIds.includes(layer.id));
  if (selected.length === 0) return <DocumentInspector />;
  if (selected.length === 1) return <SingleInspector layer={selected[0]} />;
  return <MultiInspector layers={selected} />;
}
