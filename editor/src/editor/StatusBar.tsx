import { useEditorStore, type AutosaveState } from "../editor-core/store";
import { fitViewport, clampZoom } from "./model/viewport";

/** The one-line description of what the browser store is doing. */
export function autosaveLabel(autosave: AutosaveState): string {
  if (autosave.status === "off") return "Not saved";
  if (autosave.status === "saved") return "Saved";
  return "Saving";
}

function autosaveTitle(autosave: AutosaveState): string {
  if (autosave.status === "off") {
    return "This browser is not keeping a copy. Use Save .add to keep your work.";
  }
  if (autosave.status === "saved" && autosave.savedAt) {
    const at = new Date(autosave.savedAt);
    return `Kept in this browser, last written ${at.toLocaleTimeString()}. Save .add keeps a file you can move.`;
  }
  return "Kept in this browser automatically.";
}

export function StatusBar() {
  const store = useEditorStore();
  const { doc, viewport, snapEnabled, rulersVisible, canvasSize, autosave } = store.state;
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
      <span className="ed-status-seam" aria-hidden="true" />
      {/* Autosave is a convenience, not a promise: say what it did, and when it
          cannot, so nobody relies on it to keep work it never stored. */}
      <span data-testid="autosave-status" title={autosaveTitle(autosave)}>
        {autosaveLabel(autosave)}
      </span>
      <span className="ed-sr-only" role="status" aria-live="polite">
        {selected} {selected === 1 ? "layer" : "layers"} selected. Zoom {zoomPercent}%.
      </span>
    </div>
  );
}
