import { Hand, MousePointer2, SlidersHorizontal, ZoomIn } from "lucide-react";
import { AddLayerMenu } from "../AddLayerMenu";
import { useEditorStore } from "../../editor-core/store";
import type { EditorTool } from "../types";

const buttonStyle = { minWidth: 44, minHeight: 44 } as const;

function ToolButton({ tool, label, icon: Icon }: { tool: EditorTool; label: string; icon: typeof MousePointer2 }) {
  const store = useEditorStore();
  return (
    <button
      type="button"
      className={`ed-tool-button ${store.state.tool === tool ? "is-active" : ""}`}
      aria-label={label}
      aria-pressed={store.state.tool === tool}
      style={buttonStyle}
      onClick={() => store.setTool(tool)}
    >
      <Icon size={18} strokeWidth={1.7} />
    </button>
  );
}

export function PhoneToolDock({ onOpenDrawer, sideRail = false }: { onOpenDrawer: (drawer: "layers" | "inspector") => void; sideRail?: boolean }) {
  const store = useEditorStore();
  return (
    <nav className={`ed-phone-tool-dock ${sideRail ? "is-side-rail" : ""}`} data-testid="phone-tool-dock" aria-label="Phone tools">
      <ToolButton tool="move" label="Move" icon={MousePointer2} />
      <ToolButton tool="hand" label="Hand" icon={Hand} />
      {sideRail ? null : <ToolButton tool="zoom" label="Zoom" icon={ZoomIn} />}
      <AddLayerMenu />
      <button
        type="button"
        className="ed-tool-button"
        aria-label="Layers"
        data-drawer-trigger="layers"
        style={buttonStyle}
        onClick={() => onOpenDrawer("layers")}
      >
        Layers
      </button>
      <button
        type="button"
        className="ed-tool-button"
        aria-label="Inspector"
        data-drawer-trigger="inspector"
        style={buttonStyle}
        onClick={() => {
          store.setRightTab("design");
          onOpenDrawer("inspector");
        }}
      >
        Inspect
      </button>
      <button
        type="button"
        className="ed-tool-button"
        aria-label="Effects"
        style={buttonStyle}
        onClick={() => {
          store.setRightTab("effects");
          onOpenDrawer("inspector");
        }}
      >
        <SlidersHorizontal size={18} strokeWidth={1.7} />
      </button>
    </nav>
  );
}
