import { useMemo, useState } from "react";
import { useEditorStore } from "../../editor-core/store";
import {
  changePositionAtReference,
  changeRotationAtReference,
  changeSizeAtReference,
  layerFrame,
  normalizeAngle,
  referencePoint,
  type ReferencePoint,
} from "../model/geometry";
import type { EditorLayer } from "../../editor-core/types";
import { NumericField } from "./NumericField";

const REFERENCES: ReferencePoint[] = ["nw", "n", "ne", "w", "c", "e", "sw", "s", "se"];

export interface TransformSectionProps {
  layer: EditorLayer;
}

const round = (value: number) => Math.round(value * 100) / 100;

export function TransformSection({ layer }: TransformSectionProps) {
  const store = useEditorStore();
  const [reference, setReference] = useState<ReferencePoint>("c");
  const [aspect, setAspect] = useState(false);
  const frame = useMemo(() => layerFrame(layer), [layer]);
  const position = referencePoint(frame, reference);

  const commitPosition = (axis: "x" | "y", value: number) => {
    const target = axis === "x" ? { x: value, y: position.y } : { x: position.x, y: value };
    store.setLayerTransforms({ [layer.id]: changePositionAtReference(layer, target, reference) });
  };

  const commitSize = (axis: "width" | "height", value: number) => {
    const size = {
      width: axis === "width" ? value : frame.width,
      height: axis === "height" ? value : frame.height,
    };
    store.setLayerTransforms({
      [layer.id]: changeSizeAtReference(layer, size, reference, aspect),
    });
  };

  return (
    <section className="ed-inspector-section" data-testid="transform-section" aria-label="Transform">
      <div className="ed-inspector-section-head">
        <span>Transform</span>
        <button
          type="button"
          className="ed-inspector-text-button"
          onClick={() => store.setLayerTransform(layer.id, { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 })}
        >
          Reset
        </button>
      </div>
      <div className="ed-ref-grid" role="group" aria-label="Reference point">
        {REFERENCES.map((ref) => (
          <button
            key={ref}
            type="button"
            className={`ed-ref-button ${reference === ref ? "is-active" : ""}`}
            data-ref={ref}
            aria-label={`Reference ${ref}`}
            aria-pressed={reference === ref}
            onClick={() => setReference(ref)}
          >
            <span aria-hidden="true" />
          </button>
        ))}
      </div>
      <div className="ed-field-grid">
        <NumericField label="X" value={round(position.x)} onCommit={(value) => commitPosition("x", value)} />
        <NumericField label="Y" value={round(position.y)} onCommit={(value) => commitPosition("y", value)} />
        <NumericField label="W" value={round(frame.width)} min={4} onCommit={(value) => commitSize("width", value)} />
        <NumericField label="H" value={round(frame.height)} min={4} onCommit={(value) => commitSize("height", value)} />
      </div>
      <div className="ed-field-grid is-rotation">
        <NumericField
          label="Rotation"
          value={round(normalizeAngle(layer.transform.rotation))}
          min={-180}
          max={180}
          step={1}
          suffix="°"
          onCommit={(value) => store.setLayerTransforms({
            [layer.id]: changeRotationAtReference(layer, value, reference),
          })}
        />
        <button
          type="button"
          className={`ed-aspect-switch ${aspect ? "is-on" : ""}`}
          role="switch"
          aria-checked={aspect}
          aria-label="Lock aspect ratio"
          onClick={() => setAspect((current) => !current)}
        >
          <span className="ed-aspect-track" aria-hidden="true"><i /></span>
          <span>Lock aspect</span>
        </button>
      </div>
    </section>
  );
}
