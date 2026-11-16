import { useTwinStore } from "../../twin/store";

export function InspectorPanel() {
  const store = useTwinStore();
  const { doc } = store.state;
  const selected = doc.layers.find((layer) => layer.id === doc.selectedLayerIds[0]);

  return (
    <section className="ed-panel-body ed-inspector-panel" data-testid="inspector-panel" aria-label="Inspector panel">
      <div className="ed-panel-heading">
        <span>Inspector</span>
        <span>{selected ? selected.kind : "Document"}</span>
      </div>
      {selected ? (
        <div className="ed-inspector-placeholder">
          <strong>{selected.name}</strong>
          <span>
            X {Math.round(selected.transform.x)} · Y {Math.round(selected.transform.y)}
          </span>
          <span>
            {Math.round(selected.transform.scaleX * 100)}% · {Math.round(selected.transform.rotation)}°
          </span>
        </div>
      ) : (
        <p className="ed-panel-empty">Select a layer to inspect it.</p>
      )}
    </section>
  );
}
