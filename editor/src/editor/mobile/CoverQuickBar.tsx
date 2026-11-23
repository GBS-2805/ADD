import { Hand, Layers, MousePointer2, Undo2 } from "lucide-react";
import { AddLayerMenu } from "../AddLayerMenu";
import { useEditorHistoryState, useEditorStore } from "../../editor-core/store";

const buttonStyle = { minWidth: 44, minHeight: 44 } as const;

export function CoverQuickBar({
  onOpenLayers,
  onPreview,
}: {
  onOpenLayers: () => void;
  onPreview: () => void;
}) {
  const store = useEditorStore();
  const { canUndo } = useEditorHistoryState();
  const hand = store.state.tool === "hand";
  return (
    <nav className="ed-cover-quick-bar" data-testid="cover-quick-bar" aria-label="Cover quick actions">
      <button
        type="button"
        className={`ed-tool-button ${hand ? "is-active" : ""}`}
        aria-label="Move / Hand"
        aria-pressed={hand}
        style={buttonStyle}
        onClick={() => store.setTool(hand ? "move" : "hand")}
      >
        {hand ? <Hand size={18} strokeWidth={1.7} /> : <MousePointer2 size={18} strokeWidth={1.7} />}
      </button>
      <AddLayerMenu />
      <button type="button" className="ed-tool-button" aria-label="Layers" style={buttonStyle} onClick={onOpenLayers}>
        <Layers size={18} strokeWidth={1.7} />
      </button>
      <button
        type="button"
        className="ed-tool-button"
        aria-label="Undo"
        style={buttonStyle}
        disabled={!canUndo}
        onClick={store.undo}
      >
        <Undo2 size={18} strokeWidth={1.7} />
      </button>
      <button type="button" className="ed-tool-button ed-cover-preview" aria-label="Preview" style={buttonStyle} onClick={onPreview}>
        Preview
      </button>
    </nav>
  );
}
