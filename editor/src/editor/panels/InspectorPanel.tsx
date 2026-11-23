import { memo, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useEditorDocument, useEditorStore } from "../../editor-core/store";
import { layerBounds, selectionBounds } from "../model/geometry";
import type { EditorDocument, EditorEffect, EditorLayer } from "../../editor-core/types";
import { BlendSelect, FillPicker, FontSelect } from "./controls";
import { AlignSection } from "./AlignSection";
import { NumericField } from "./NumericField";
import { TransformSection } from "./TransformSection";
import { useRenderSpan } from "../perfProfile";

const round = (value: number) => Math.round(value * 100) / 100;

function backgroundLabel(effect: EditorEffect | null | undefined): string {
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

function DocumentInspector({ doc }: { doc: EditorDocument }) {
  const store = useEditorStore();
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

function SingleInspector({ doc, layer, stage }: { doc: EditorDocument; layer: EditorLayer; stage: number }) {
  const store = useEditorStore();
  const bounds = layerBounds(layer);
  return (
    <section className="ed-panel-body ed-inspector-panel" data-testid="inspector-panel" aria-label="Inspector panel">
      <div className="ed-panel-heading">
        <span>{layer.name}</span>
        <span>{layer.kind}</span>
      </div>
      <TransformSection layer={layer} />
      {stage >= 1 ? (
        <>
          <AlignSection layers={doc.layers} selectedIds={[layer.id]} />
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
        </>
      ) : null}
      {stage >= 2 && layer.kind === "text" && layer.text ? (
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
      {stage >= 2 && layer.kind === "image" && layer.image ? (
        <Section title="Image">
          <div className="ed-document-readout">
            <span>Source</span><strong>{layer.image.width} × {layer.image.height} px</strong>
          </div>
        </Section>
      ) : null}
      {stage >= 2 && layer.kind === "shape" ? (
        <Section title="Shape">
          <p className="ed-panel-empty">{layer.shape?.variant ?? "star"} shape · every edit is undoable.</p>
        </Section>
      ) : null}
      {stage >= 2 && layer.effect ? (
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
      {stage >= 2 && layer.kind !== "image" ? (
        <Section title="Fill"><FillPicker layer={layer} /></Section>
      ) : null}
    </section>
  );
}

function MultiInspector({ layers, selectedIds }: { layers: EditorLayer[]; selectedIds: string[] }) {
  const bounds = selectionBounds(layers, selectedIds);
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
      <AlignSection layers={layers} selectedIds={selectedIds} />
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

/**
 * The inspector reads a deferred document: a transform commit can paint the
 * canvas (and settle the drag) before the readout catches up one frame later.
 * Memoizing the body is what makes the urgent render skip it at all.
 */
const InspectorBody = memo(function InspectorBody({ doc, stage }: { doc: EditorDocument; stage: number }) {
  useRenderSpan("inspector-panel");
  const selected = doc.layers.filter((layer) => doc.selectedLayerIds.includes(layer.id));
  if (selected.length === 0) return <DocumentInspector doc={doc} />;
  if (selected.length === 1) return <SingleInspector doc={doc} layer={selected[0]} stage={stage} />;
  return <MultiInspector layers={selected} selectedIds={doc.selectedLayerIds} />;
});

function sameSelection(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id, index) => id === b[index]);
}

/**
 * Opening a project replaces the selection, and mounting the full inspector for
 * that new layer in the open commit cost 23 ms at 4x CPU. A replaced document
 * brings its sections in over the next two frames; ordinary selections still
 * render the whole inspector immediately.
 */
function useInspectorStage(doc: EditorDocument): number {
  const ids = useMemo(() => doc.layers.map((layer) => layer.id), [doc.layers]);
  const signature = useMemo(() => ids.join("\u0000"), [ids]);
  const previous = useRef(new Set(ids));
  const [reveal, setReveal] = useState<{ signature: string; stage: number }>({ signature, stage: 2 });
  const changed = reveal.signature !== signature;
  const replacement =
    changed &&
    ids.filter((id) => !previous.current.has(id)).length > Math.max(1, ids.length / 2);

  useEffect(() => {
    if (!changed) return;
    const replacing =
      ids.filter((id) => !previous.current.has(id)).length > Math.max(1, ids.length / 2);
    previous.current = new Set(ids);
    if (!replacing) {
      setReveal({ signature, stage: 2 });
      return;
    }
    setReveal({ signature, stage: 0 });
    const frames: number[] = [];
    frames.push(
      requestAnimationFrame(() => {
        setReveal({ signature, stage: 1 });
        frames.push(requestAnimationFrame(() => setReveal({ signature, stage: 2 })));
      }),
    );
    return () => frames.forEach((frame) => cancelAnimationFrame(frame));
  }, [changed, ids, signature]);

  return replacement ? 0 : reveal.stage;
}

export function InspectorPanel() {
  const { doc } = useEditorDocument();
  const deferredDoc = useDeferredValue(doc);
  const stage = useInspectorStage(doc);
  // A new selection must reach the fields immediately: the same opacity field
  // exists for every layer, so deferring it would let the next keystroke edit
  // the previous layer. Only a same-selection change (a transform) may lag.
  const shownDoc = sameSelection(doc.selectedLayerIds, deferredDoc.selectedLayerIds) ? deferredDoc : doc;
  return <InspectorBody doc={shownDoc} stage={stage} />;
}
