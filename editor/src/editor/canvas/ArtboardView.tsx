import type { CSSProperties, KeyboardEvent, MouseEvent } from "react";
import { EffectSurface } from "../../editor-core/ShaderView";
import type { LayerTransform, EditorDocument } from "../../editor-core/types";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "../model/geometry";
import { LayerView } from "./LayerView";

export interface ArtboardViewProps {
  doc: EditorDocument;
  transforms?: Record<string, LayerTransform>;
  selectedIds?: string[];
  readOnly?: boolean;
  testId?: string;
  /** Preview/print surfaces must not duplicate the editor's DOM test ids. */
  exposeLayerIds?: boolean;
  className?: string;
  onLayerSelect?: (id: string, event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => void;
}

export function ArtboardView({
  doc,
  transforms,
  selectedIds,
  readOnly = false,
  testId = "artboard",
  exposeLayerIds = true,
  className = "",
  onLayerSelect,
}: ArtboardViewProps) {
  const selection = selectedIds ?? doc.selectedLayerIds;
  const style: CSSProperties = {
    width: ARTBOARD_WIDTH,
    height: ARTBOARD_HEIGHT,
  };

  return (
    <div
      className={`artboard ed-artboard ${readOnly ? "is-readonly" : ""} ${className}`.trim()}
      style={style}
      data-testid={testId}
      data-artboard={readOnly ? "readonly" : "editable"}
      data-artboard-width={ARTBOARD_WIDTH}
      data-artboard-height={ARTBOARD_HEIGHT}
      role="presentation"
    >
      <div className="artboard-ground">
        <EffectSurface effect={doc.background} />
      </div>
      <div className="artboard-grain" aria-hidden="true" />
      <div className="ed-layer-stack">
        {doc.layers.map((layer) => (
          <LayerView
            key={layer.id}
            layer={layer}
            transform={transforms?.[layer.id] ?? layer.transform}
            selected={selection.includes(layer.id)}
            readOnly={readOnly}
            testId={exposeLayerIds ? undefined : null}
            layerId={exposeLayerIds ? undefined : null}
            onLayerSelect={onLayerSelect}
          />
        ))}
      </div>
      <div className="artboard-meta" aria-hidden="true">
        <span>VOL. 01</span>
        <span>PRINT / 2026</span>
      </div>
    </div>
  );
}
