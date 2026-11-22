import type { CSSProperties } from "react";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH, layerFrame, selectionBounds, type Frame, type Rect } from "../model/geometry";
import type { SnapGuide } from "../model/snapping";
import type { LayerTransform, EditorLayer } from "../../editor-core/types";

export interface SelectionOverlayProps {
  layers: EditorLayer[];
  selectedIds: string[];
  transforms?: Record<string, LayerTransform>;
  guides?: SnapGuide[];
  marquee?: Rect | null;
}

const HANDLES = ["nw", "n", "ne", "e", "se", "s", "sw", "w"] as const;

function handleStyle(handle: (typeof HANDLES)[number]): CSSProperties {
  const vertical = handle.includes("n") ? "0%" : handle.includes("s") ? "100%" : "50%";
  const horizontal = handle.includes("w") ? "0%" : handle.includes("e") ? "100%" : "50%";
  return { left: horizontal, top: vertical };
}

function SingleSelection({ frame, name }: { frame: Frame; name: string }) {
  return (
    <div
      className="ed-selection-single"
      style={{
        left: ARTBOARD_WIDTH / 2 + frame.x - frame.width / 2,
        top: ARTBOARD_HEIGHT / 2 + frame.y - frame.height / 2,
        width: frame.width,
        height: frame.height,
        transform: `rotate(${frame.rotation}deg)`,
      }}
    >
      <span className="ed-selection-label">{name}</span>
      {HANDLES.map((handle) => (
        <span
          key={handle}
          className={`ed-handle ed-handle-${handle}`}
          style={handleStyle(handle)}
          data-handle={handle}
          role="presentation"
          aria-hidden="true"
          tabIndex={-1}
        />
      ))}
      <span
        className="ed-rotate-handle"
        data-handle="rotate"
        role="presentation"
        aria-hidden="true"
        tabIndex={-1}
      />
    </div>
  );
}

export function SelectionOverlay({ layers, selectedIds, transforms = {}, guides = [], marquee = null }: SelectionOverlayProps) {
  const selected = layers.filter((layer) => selectedIds.includes(layer.id));
  const single = selected.length === 1 ? selected[0] : null;
  const frame = single ? layerFrame(single, transforms[single.id] ?? single.transform) : null;
  const bounds = selected.length > 1 ? selectionBounds(layers, selectedIds) : null;

  return (
    <div
      className="ed-selection-overlay"
      data-testid="selection-overlay"
      data-selection-count={selected.length}
      aria-hidden="true"
      style={{ pointerEvents: "none" }}
    >
      {frame ? <SingleSelection frame={frame} name={single?.name ?? ""} /> : null}
      {bounds ? (
        <div
          className="ed-selection-multi"
          style={{ left: bounds.x, top: bounds.y, width: bounds.width, height: bounds.height }}
        >
          <span className="ed-selection-label">{selected.length} layers</span>
        </div>
      ) : null}
      {guides.map((guide) => (
        <div
          key={`${guide.axis}-${guide.position}`}
          className={`ed-snap-guide ed-snap-guide-${guide.axis}`}
          data-guide-axis={guide.axis}
          style={guide.axis === "x"
            ? { left: guide.position, top: 0, height: 1000 }
            : { top: guide.position, left: 0, width: 765 }}
        />
      ))}
      {marquee && marquee.width > 0 && marquee.height > 0 ? (
        <div
          className="ed-marquee"
          data-testid="marquee"
          style={{ left: marquee.x, top: marquee.y, width: marquee.width, height: marquee.height }}
        />
      ) : null}
    </div>
  );
}
