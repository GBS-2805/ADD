import { useTwinStore } from "../twin/store";

export function StatusBar() {
  const store = useTwinStore();
  const { doc, viewport, snapEnabled } = store.state;
  const selected = doc.selectedLayerIds.length;

  return (
    <div className="ed-status-bar" data-testid="status-bar" role="status" aria-live="polite">
      <span>{Math.round(viewport.zoom * 100)}%</span>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>
        {doc.layers.length} {doc.layers.length === 1 ? "layer" : "layers"}
      </span>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>{selected ? `${selected} selected` : "Nothing selected"}</span>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>{snapEnabled ? "Snap on" : "Snap off"}</span>
    </div>
  );
}
