import { useTwinStore } from "../twin/store";
import { fitViewport, clampZoom } from "./model/viewport";

export function StatusBar() {
  const store = useTwinStore();
  const { doc, viewport, snapEnabled, rulersVisible, canvasSize } = store.state;
  const selected = doc.selectedLayerIds.length;
  const zoomPercent = Math.round(viewport.zoom * 100);

  const fit = () => {
    if (canvasSize.width > 0 && canvasSize.height > 0) store.setViewport(fitViewport(canvasSize));
  };

  return (
    <div className="ed-status-bar" data-testid="status-bar">
      <div className="ed-zoom-controls" aria-label="Zoom">
        <button
          type="button"
          className="ed-status-button"
          data-testid="zoom-out"
          aria-label="Zoom out"
          onClick={() => store.setViewport({ ...viewport, zoom: clampZoom(viewport.zoom / 1.25) })}
        >
          −
        </button>
        <span className="ed-zoom-value">{zoomPercent}%</span>
        <button
          type="button"
          className="ed-status-button"
          data-testid="zoom-in"
          aria-label="Zoom in"
          onClick={() => store.setViewport({ ...viewport, zoom: clampZoom(viewport.zoom * 1.25) })}
        >
          +
        </button>
        <button type="button" className="ed-status-button" data-testid="zoom-fit" aria-label="Fit artboard" onClick={fit}>
          Fit
        </button>
        <button
          type="button"
          className="ed-status-button"
          data-testid="zoom-100"
          aria-label="Zoom to 100%"
          onClick={() => store.setViewport({ zoom: 1, panX: 0, panY: 0 })}
        >
          100%
        </button>
        <button
          type="button"
          className="ed-status-button"
          data-testid="toggle-rulers"
          aria-label={rulersVisible ? "Hide rulers" : "Show rulers"}
          aria-pressed={rulersVisible}
          onClick={() => store.setRulersVisible(!rulersVisible)}
        >
          {rulersVisible ? "Rulers on" : "Rulers"}
        </button>
      </div>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>
        {doc.layers.length} {doc.layers.length === 1 ? "layer" : "layers"}
      </span>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>{selected ? `${selected} selected` : "Nothing selected"}</span>
      <span className="ed-status-seam" aria-hidden="true" />
      <span>{snapEnabled ? "Snap on" : "Snap off"}</span>
      <span className="ed-sr-only" role="status" aria-live="polite">
        {selected} {selected === 1 ? "layer" : "layers"} selected. Zoom {zoomPercent}%.
      </span>
    </div>
  );
}
