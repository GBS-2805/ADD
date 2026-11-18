import { Hand, MousePointer2, SlidersHorizontal, ZoomIn } from "lucide-react";
import { useEditorStore } from "../editor-core/store";
import type { EditorTool } from "./types";
import { AddLayerMenu } from "./AddLayerMenu";

const tools: { value: EditorTool; label: string; shortcut: string; icon: typeof MousePointer2 }[] = [
  { value: "move", label: "Move tool", shortcut: "V", icon: MousePointer2 },
  { value: "hand", label: "Hand tool", shortcut: "H", icon: Hand },
  { value: "zoom", label: "Zoom tool", shortcut: "Z", icon: ZoomIn },
];

export function Toolbar() {
  const store = useEditorStore();

  return (
    <nav className="ed-tool-pill" aria-label="Editor tools">
      {/* One tool at a time, so these are pressed buttons rather than a
          multi-select group; the pressed style already lives on aria-pressed. */}
      <div className="ed-tool-group" role="group" aria-label="Active tool">
        {tools.map((tool) => {
          const Icon = tool.icon;
          return (
            <button
              key={tool.value}
              type="button"
              className="ed-tool-button"
              aria-label={tool.label}
              aria-pressed={store.state.tool === tool.value}
              title={`${tool.label} · ${tool.shortcut}`}
              style={{ minWidth: 44, minHeight: 44 }}
              onClick={() => store.setTool(tool.value as EditorTool)}
            >
              <Icon size={18} strokeWidth={1.7} />
            </button>
          );
        })}
      </div>
      <span className="ed-tool-seam" aria-hidden="true" />
      <AddLayerMenu />
      <button
        type="button"
        className="ed-tool-button"
        aria-label="Effects"
        title="Effects panel"
        style={{ minWidth: 44, minHeight: 44 }}
        onClick={() => store.setRightTab("effects")}
      >
        <SlidersHorizontal size={18} strokeWidth={1.7} />
      </button>
    </nav>
  );
}
