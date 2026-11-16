import { useRef, useState } from "react";
import { Menu, Portal } from "@ark-ui/react";
import { ChevronRight, Image, Plus, Shapes, Type } from "lucide-react";
import { useTwinStore } from "../twin/store";
import type { ShapeVariant } from "../twin/types";

const SHAPES: { label: string; variant: ShapeVariant }[] = [
  { label: "Star", variant: "star" },
  { label: "Orbit", variant: "orbit" },
  { label: "Block", variant: "block" },
];

export function AddMenu() {
  const store = useTwinStore();
  const addRef = useRef<HTMLButtonElement>(null);
  const [menuKey, setMenuKey] = useState(0);
  const active = store.state.surface === "inspector" || store.state.activePluginId === "overlay-lab";

  return (
    <Menu.Root key={menuKey} aria-label="Add a layer" positioning={{ placement: "top-start", gutter: 10 }}>
      <Menu.Trigger asChild>
        <button
          ref={addRef}
          type="button"
          className={active ? "dock-target is-active" : "dock-target"}
          aria-label="Add a layer"
        >
          <Plus size={19} strokeWidth={1.7} />
          <span>Add</span>
        </button>
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content className="add-menu-content" data-testid="add-menu">
            <Menu.Item value="text" className="add-menu-item" onSelect={store.addTextLayer}>
              <Type size={15} strokeWidth={1.7} />
              <span>Text</span>
              <small>Type</small>
            </Menu.Item>
            <Menu.Root positioning={{ placement: "right-start", gutter: 5 }}>
              <Menu.Context>
                {(submenu) => (
                  <Menu.TriggerItem
                    className="add-menu-item add-menu-subtrigger"
                    onPointerMove={() => submenu.setOpen(true)}
                    onPointerDown={() => submenu.setOpen(true)}
                    onClickCapture={(event) => {
                      event.preventDefault();
                      event.stopPropagation();
                      submenu.setOpen(true);
                    }}
                    onKeyDown={(event) => {
                      if (event.key !== "ArrowRight") return;
                      event.preventDefault();
                      submenu.setOpen(true);
                    }}
                  >
                    <Shapes size={15} strokeWidth={1.7} />
                    <span>Shape</span>
                    <ChevronRight className="add-menu-caret" size={14} strokeWidth={1.7} />
                  </Menu.TriggerItem>
                )}
              </Menu.Context>
              <Portal>
                <Menu.Positioner>
                  <Menu.Content className="add-menu-content add-menu-submenu">
                    {SHAPES.map((shape) => (
                      <Menu.Item
                        key={shape.variant}
                        value={shape.variant}
                        className="add-menu-item"
                        onSelect={() => {
                          store.addShapeLayer(shape.variant);
                          setMenuKey((key) => key + 1);
                          requestAnimationFrame(() => addRef.current?.focus());
                        }}
                      >
                        <span className={`add-menu-shape add-menu-shape-${shape.variant}`} aria-hidden="true" />
                        <span>{shape.label}</span>
                        <small>Shape</small>
                      </Menu.Item>
                    ))}
                  </Menu.Content>
                </Menu.Positioner>
              </Portal>
            </Menu.Root>
            <Menu.Separator className="add-menu-separator" />
            <Menu.Item
              value="png"
              className="add-menu-item"
              onSelect={() => store.openPlugin("overlay-lab")}
            >
              <Image size={15} strokeWidth={1.7} />
              <span>PNG</span>
              <small>Overlay</small>
            </Menu.Item>
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  );
}
