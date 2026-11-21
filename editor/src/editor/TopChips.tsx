import { Menu, Portal } from "@ark-ui/react";
import { Eye, FileDown, FolderOpen, Printer, Redo2, Save, Undo2 } from "lucide-react";
import { useEditorStore } from "../editor-core/store";

export function TopChips() {
  const store = useEditorStore();
  const { nativeLens } = store.state;

  return (
    <div className="ed-top-chips">
      <Menu.Root positioning={{ placement: "bottom-start", gutter: 8 }}>
        <Menu.Trigger asChild>
          <button type="button" className="ed-document-chip" aria-label="Open document menu">
            <span className="ed-document-title">OPEN STUDIO</span>
            <span className="ed-document-meta">TWIN / 765 × 1000</span>
          </button>
        </Menu.Trigger>
        <Portal>
          <Menu.Positioner>
            <Menu.Content className="ed-menu">
              <Menu.Item value="save" onSelect={() => void store.saveProject()}>
                <Save size={15} strokeWidth={1.7} /> Save .add
              </Menu.Item>
              <Menu.Item value="open" onSelect={store.openProject}>
                <FolderOpen size={15} strokeWidth={1.7} /> Open .add
              </Menu.Item>
              <Menu.Item value="print" onSelect={store.printStage}>
                <Printer size={15} strokeWidth={1.7} /> Print / Save PDF
              </Menu.Item>
              <Menu.Separator />
              <Menu.Item value="export" onSelect={() => void store.exportPng()}>
                <FileDown size={15} strokeWidth={1.7} /> Export PNG
              </Menu.Item>
            </Menu.Content>
          </Menu.Positioner>
        </Portal>
      </Menu.Root>

      <div className="ed-top-actions">
        <div className="ed-history-buttons" role="group" aria-label="History">
          <button
            type="button"
            className="ed-chip-button"
            aria-label="Undo"
            title="Undo · ⌘Z"
            disabled={!store.canUndo}
            onClick={store.undo}
          >
            <Undo2 size={15} strokeWidth={1.7} />
          </button>
          <button
            type="button"
            className="ed-chip-button"
            aria-label="Redo"
            title="Redo · ⇧⌘Z"
            disabled={!store.canRedo}
            onClick={store.redo}
          >
            <Redo2 size={15} strokeWidth={1.7} />
          </button>
        </div>
        <button
          type="button"
          className="ed-preview-button"
          aria-label="Preview"
          data-testid="preview-trigger"
          title="Preview · read-only stage"
          onClick={store.openPresentation}
        >
          <Eye size={15} strokeWidth={1.7} /> Preview
        </button>
        <button
          type="button"
          className={`ed-lens-button ${nativeLens ? "is-active" : ""}`}
          aria-label="Native lens"
          aria-pressed={nativeLens}
          title="Label what still needs native work"
          onClick={store.toggleNativeLens}
        >
          <span className="ed-lens-dot" /> Native lens
        </button>
      </div>
    </div>
  );
}
