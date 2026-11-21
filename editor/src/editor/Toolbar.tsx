import { ToggleGroup } from "@ark-ui/react";
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
      <ToggleGroup.Root
        multiple
        className="ed-tool-group"
        value={[store.state.tool]}
        onValueChange={({ value }) => {
          if (value[0]) store.setTool(value[0] as EditorTool);
        }}
      >
        {tools.map((tool) => {
          const Icon = tool.icon;
          return (
            <ToggleGroup.Item
              key={tool.value}
              value={tool.value}
              className="ed-tool-button"
              aria-label={tool.label}
              title={`${tool.label} · ${tool.shortcut}`}
              tabIndex={0}
              style={{ minWidth: 44, minHeight: 44 }}
            >
              <Icon size={18} strokeWidth={1.7} />
            </ToggleGroup.Item>
          );
        })}
      </ToggleGroup.Root>
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
