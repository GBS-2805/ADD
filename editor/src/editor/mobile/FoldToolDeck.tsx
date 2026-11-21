import { useState } from "react";
import { Hand, MousePointer2, ZoomIn } from "lucide-react";
import { AddLayerMenu } from "../AddLayerMenu";
import { InspectorPanel } from "../panels/InspectorPanel";
import { LayersPanel } from "../panels/LayersPanel";
import { useEditorStore } from "../../editor-core/store";

type FoldTab = "tools" | "layers" | "properties";

export function FoldToolDeck() {
  const store = useEditorStore();
  const [tab, setTab] = useState<FoldTab>("tools");
  return (
    <section className="ed-fold-deck" data-testid="fold-tool-deck" aria-label="Fold tools">
      <div className="ed-fold-tabs" role="tablist" aria-label="Fold tool sections">
        {(["tools", "layers", "properties"] as const).map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={tab === item}
            onClick={() => setTab(item)}
            style={{ minHeight: 44 }}
          >
            {item === "tools" ? "Tools" : item === "layers" ? "Layers" : "Properties"}
          </button>
        ))}
      </div>
      <div className="ed-fold-deck-body">
        {tab === "tools" ? (
          <div className="ed-fold-tools">
            <button type="button" className="ed-fold-button" aria-label="Move" aria-pressed={store.state.tool === "move"} onClick={() => store.setTool("move")}>
              <MousePointer2 size={18} strokeWidth={1.7} />
            </button>
            <button type="button" className="ed-fold-button" aria-label="Hand" aria-pressed={store.state.tool === "hand"} onClick={() => store.setTool("hand")}>
              <Hand size={18} strokeWidth={1.7} />
            </button>
            <button type="button" className="ed-fold-button" aria-label="Zoom" aria-pressed={store.state.tool === "zoom"} onClick={() => store.setTool("zoom")}>
              <ZoomIn size={18} strokeWidth={1.7} />
            </button>
            <AddLayerMenu />
            <button type="button" className="ed-fold-button" aria-label="Undo" disabled={!store.canUndo} onClick={store.undo}>
              Undo
            </button>
          </div>
        ) : tab === "layers" ? (
          <LayersPanel />
        ) : (
          <InspectorPanel />
        )}
      </div>
    </section>
  );
}
