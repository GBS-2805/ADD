import { useRef, useState, type ChangeEvent } from "react";
import { Menu, Portal } from "@ark-ui/react";
import { Image, Plus, Shapes, Type } from "lucide-react";
import { useEditorStore } from "../editor-core/store";

export function AddLayerMenu() {
  const store = useEditorStore();
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const importPng = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || busy) return;
    setBusy(true);
    try {
      await store.addImageLayer(file);
    } catch {
      // The store owns the user-facing error toast.
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Menu.Root positioning={{ placement: "top-start", gutter: 8 }}>
        <Menu.Trigger asChild>
          <button
            type="button"
            className="ed-tool-button ed-add-button"
            aria-label="Add layer"
            title="Add text, shape or PNG"
            style={{ minWidth: 44, minHeight: 44 }}
          >
            <Plus size={18} strokeWidth={1.7} />
          </button>
        </Menu.Trigger>
        <Portal>
          <Menu.Positioner>
            <Menu.Content className="ed-menu ed-add-menu">
              <Menu.Item value="text" onSelect={() => store.addTextLayer()}>
                <Type size={15} strokeWidth={1.7} /> Text
              </Menu.Item>
              <Menu.Separator />
              <Menu.Item value="star" onSelect={() => store.addShapeLayer("star")}>
                <Shapes size={15} strokeWidth={1.7} /> Shape / Star
              </Menu.Item>
              <Menu.Item value="orbit" onSelect={() => store.addShapeLayer("orbit")}>
                <Shapes size={15} strokeWidth={1.7} /> Shape / Orbit
              </Menu.Item>
              <Menu.Item value="block" onSelect={() => store.addShapeLayer("block")}>
                <Shapes size={15} strokeWidth={1.7} /> Shape / Block
              </Menu.Item>
              <Menu.Separator />
              <Menu.Item value="png" onSelect={() => inputRef.current?.click()}>
                <Image size={15} strokeWidth={1.7} /> PNG
              </Menu.Item>
            </Menu.Content>
          </Menu.Positioner>
        </Portal>
      </Menu.Root>
      <input
        ref={inputRef}
        type="file"
        accept="image/png"
        hidden
        data-testid="editor-image-input"
        onChange={(event) => void importPng(event)}
      />
    </>
  );
}
