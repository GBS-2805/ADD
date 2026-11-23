import { useEffect, useMemo, useRef, useState, type DragEvent, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent as ReactMouseEvent } from "react";
import { Menu, Portal } from "@ark-ui/react";
import { TreeView } from "@ark-ui/react/tree-view";
import { Eye, EyeOff, GripVertical, Image as ImageIcon, MoreHorizontal, Shapes, Type } from "lucide-react";
import { useEditorDocument, useEditorStore } from "../../editor-core/store";
import type { EditorLayer } from "../../editor-core/types";
import { createLayerCollection, displayLayers, type LayerTreeNode } from "../model/layerTree";
import type { SelectionMode } from "../model/selection";
import { usePointerReorder } from "./usePointerReorder";
import { AddLayerMenu } from "../AddLayerMenu";

type RenameState = { id: string; value: string } | null;

const kindIcon = (kind?: EditorLayer["kind"]) => {
  if (kind === "text") return <Type size={12} strokeWidth={1.8} aria-hidden="true" />;
  if (kind === "image") return <ImageIcon size={12} strokeWidth={1.8} aria-hidden="true" />;
  return <Shapes size={12} strokeWidth={1.8} aria-hidden="true" />;
};

function pointerIsFine(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(pointer: fine)").matches;
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(target.closest("button, input, textarea, [role='menu'], [data-part='content']"));
}

function LayerGrip({ rowId, name, onReorder }: { rowId: string; name: string; onReorder: (from: string, to: string) => void }) {
  const { gripProps } = usePointerReorder({ rowId, onReorder });
  return (
    <button
      type="button"
      className="ed-layer-grip"
      aria-label={`Reorder ${name}`}
      title="Drag to reorder"
      {...gripProps}
    >
      <GripVertical size={14} strokeWidth={1.7} />
    </button>
  );
}

export function LayersPanel({ collapsed = false, compact = false }: { collapsed?: boolean; compact?: boolean }) {
  const store = useEditorStore();
  const { doc } = useEditorDocument();
  const [renaming, setRenaming] = useState<RenameState>(null);
  const [dragId, setDragId] = useState<string | null>(null);
  const suppressArkSelection = useRef(false);
  const fine = pointerIsFine();
  const collection = useMemo(() => createLayerCollection(doc.layers), [doc.layers]);
  const display = useMemo(() => displayLayers(doc.layers), [doc.layers]);
  const selectedDisplayIds = doc.selectedLayerIds;

  const rowById = (id: string) => {
    const safe = typeof CSS !== "undefined" && CSS.escape ? CSS.escape(id) : id;
    return document.querySelector<HTMLElement>(`[data-layer-row="${safe}"]`);
  };

  const focusRow = (id: string | undefined) => {
    if (!id) return;
    const row = rowById(id);
    row?.focus();
    row?.scrollIntoView?.({ block: "nearest" });
  };

  useEffect(() => {
    const last = doc.selectedLayerIds[doc.selectedLayerIds.length - 1];
    if (!last) return;
    rowById(last)?.scrollIntoView?.({ block: "nearest" });
    // Selecting from the canvas must not steal keyboard focus from the canvas.
  }, [doc.selectedLayerIds.join("|")]);

  const selectFromRow = (id: string, event: ReactMouseEvent) => {
    const mode: SelectionMode = event.shiftKey ? "add" : event.metaKey || event.ctrlKey ? "toggle" : "replace";
    suppressArkSelection.current = true;
    store.selectLayers([id], mode);
  };

  const onClickCapture = (event: ReactMouseEvent) => {
    if (isInteractiveTarget(event.target)) return;
    const row = event.target instanceof Element ? event.target.closest<HTMLElement>("[data-layer-row]") : null;
    if (!row?.dataset.layerRow) return;
    if (renaming) return;
    selectFromRow(row.dataset.layerRow, event);
  };

  const reorderToTarget = (fromId: string, toId: string) => {
    if (fromId === toId) return;
    const ids = doc.selectedLayerIds.includes(fromId) ? doc.selectedLayerIds : [fromId];
    const fromIndex = display.findIndex((layer) => layer.id === fromId);
    const targetIndex = display.findIndex((layer) => layer.id === toId);
    if (fromIndex < 0 || targetIndex < 0) return;
    const moving = display.filter((layer) => ids.includes(layer.id));
    const without = display.filter((layer) => !ids.includes(layer.id));
    const targetWithout = without.findIndex((layer) => layer.id === toId);
    if (targetWithout < 0) return;
    const insertAt = fromIndex < targetIndex ? targetWithout + 1 : targetWithout;
    const withoutDoc = [...without].reverse();
    store.reorderLayers(ids, withoutDoc.length - insertAt);
  };

  const deleteLayers = (ids: string[], focusedId: string) => {
    const index = display.findIndex((layer) => layer.id === focusedId);
    const remaining = display.filter((layer) => !ids.includes(layer.id));
    const nextFocus = remaining[Math.min(Math.max(index, 0), remaining.length - 1)];
    store.removeSelectedLayers(ids);
    window.setTimeout(() => {
      if (nextFocus && rowById(nextFocus.id)) focusRow(nextFocus.id);
      else document.querySelector<HTMLElement>('[data-testid="canvas-surface"]')?.focus();
    }, 0);
  };

  const onKeyDownCapture = (event: ReactKeyboardEvent) => {
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
    const row = event.target instanceof Element ? event.target.closest<HTMLElement>("[data-layer-row]") : null;
    if (!row?.dataset.layerRow) return;

    if (event.key === "ArrowUp" || event.key === "ArrowDown") {
      event.preventDefault();
      event.stopPropagation();
      const index = display.findIndex((layer) => layer.id === row.dataset.layerRow);
      const next = display[index + (event.key === "ArrowDown" ? 1 : -1)];
      if (!next) return;
      store.selectLayers([next.id], event.shiftKey ? "add" : "replace");
      window.setTimeout(() => focusRow(next.id), 0);
      return;
    }

    if (!compact && (event.key === "Delete" || event.key === "Backspace")) {
      event.preventDefault();
      event.stopPropagation();
      const focusedId = row.dataset.layerRow;
      const ids = doc.selectedLayerIds.includes(focusedId) ? doc.selectedLayerIds : [focusedId];
      deleteLayers(ids, focusedId);
    }
  };

  const onDrop = (event: DragEvent, targetId: string) => {
    event.preventDefault();
    event.stopPropagation();
    if (!dragId) return;
    reorderToTarget(dragId, targetId);
    setDragId(null);
  };

  const renderRow = (node: LayerTreeNode, indexPath: number[]) => {
    const layer = doc.layers.find((item) => item.id === node.id);
    if (!layer) return null;
    const displayIndex = display.findIndex((item) => item.id === layer.id);
    const selected = doc.selectedLayerIds.includes(layer.id);
    const isRenaming = renaming?.id === layer.id;

    return (
      <TreeView.NodeProvider key={node.id} node={node} indexPath={indexPath}>
        <TreeView.Item
          className={`ed-layer-row ${selected ? "is-selected" : ""} ${dragId === layer.id ? "is-dragging" : ""}`}
          data-testid={`layer-row-${layer.id}`}
          data-layer-row={layer.id}
          draggable={fine ? true : undefined}
          onDragStart={(event) => {
            if (!fine) return;
            setDragId(layer.id);
            event.dataTransfer.effectAllowed = "move";
            event.dataTransfer.setData("text/plain", layer.id);
          }}
          onDragEnd={() => setDragId(null)}
          onDragOver={(event) => {
            if (!fine) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = "move";
          }}
          onDrop={(event) => onDrop(event, layer.id)}
        >
          <span className="ed-layer-kind" aria-hidden="true">
            {kindIcon(layer.kind)}
          </span>
          {isRenaming ? (
            <input
              className="ed-layer-rename"
              data-testid="layer-rename-input"
              aria-label={`Rename ${layer.name}`}
              value={renaming.value}
              ref={(input) => {
                if (input && document.activeElement !== input) {
                  input.focus();
                  input.select();
                }
              }}
              onChange={(event) => setRenaming({ id: layer.id, value: event.target.value })}
              onBlur={() => {
                const next = renaming.value.trim();
                if (next && next !== layer.name) store.renameLayer(layer.id, next);
                setRenaming(null);
              }}
              onKeyDown={(event) => {
                event.stopPropagation();
                if (event.key === "Escape") {
                  event.preventDefault();
                  setRenaming(null);
                } else if (event.key === "Enter") {
                  event.preventDefault();
                  const next = renaming.value.trim();
                  if (next) store.renameLayer(layer.id, next);
                  setRenaming(null);
                }
              }}
            />
          ) : (
            <TreeView.ItemText
              className="ed-layer-name"
              title={layer.name}
              onDoubleClick={(event) => {
                if (compact) return;
                event.stopPropagation();
                setRenaming({ id: layer.id, value: layer.name });
              }}
            >
              {layer.name}
            </TreeView.ItemText>
          )}

          <button
            type="button"
            className="ed-layer-action ed-layer-eye"
            aria-label={layer.visible ? `Hide ${layer.name}` : `Show ${layer.name}`}
            title={layer.visible ? "Hide layer" : "Show layer"}
            onClick={(event) => {
              event.stopPropagation();
              store.setLayerVisibility(layer.id, !layer.visible);
            }}
          >
            {layer.visible ? <Eye size={13} strokeWidth={1.7} /> : <EyeOff size={13} strokeWidth={1.7} />}
          </button>

          {compact ? null : <Menu.Root positioning={{ placement: "bottom-end", gutter: 4 }}>
            <Menu.Trigger asChild>
              <button
                type="button"
                className="ed-layer-action"
                aria-label={`Layer actions for ${layer.name}`}
                title="Layer actions"
                onClick={(event) => event.stopPropagation()}
              >
                <MoreHorizontal size={14} strokeWidth={1.7} />
              </button>
            </Menu.Trigger>
            <Portal>
              <Menu.Positioner>
                <Menu.Content className="ed-menu ed-layer-menu">
                  <Menu.Item value="up" disabled={displayIndex === 0} onSelect={() => store.moveLayer(layer.id, "up")}>
                    Move up
                  </Menu.Item>
                  <Menu.Item
                    value="down"
                    disabled={displayIndex === display.length - 1}
                    onSelect={() => store.moveLayer(layer.id, "down")}
                  >
                    Move down
                  </Menu.Item>
                  <Menu.Separator />
                  <Menu.Item value="delete" onSelect={() => deleteLayers([layer.id], layer.id)}>
                    Delete layer
                  </Menu.Item>
                </Menu.Content>
              </Menu.Positioner>
            </Portal>
          </Menu.Root>}

          {compact ? null : <LayerGrip rowId={layer.id} name={layer.name} onReorder={reorderToTarget} />}
        </TreeView.Item>
      </TreeView.NodeProvider>
    );
  };

  return (
    <section
      className="ed-panel-body ed-layers-panel"
      data-testid="layers-panel"
      data-collapsed={collapsed ? "true" : "false"}
      aria-label="Layers panel"
    >
      {compact ? (
        <p className="ed-cover-limit">Quick Mode · select and show/hide layers here. Use the unfolded editor for transforms, reorder and delete.</p>
      ) : null}
      <div className="ed-panel-heading">
        <span>Layers</span>
        <span>{doc.layers.length} layer{doc.layers.length === 1 ? "" : "s"}</span>
      </div>

      {doc.layers.length === 0 ? (
        <div className="ed-layer-empty">
          <p>No layers. Add text, a shape or a PNG.</p>
          <AddLayerMenu />
        </div>
      ) : (
        <div className="ed-layer-scroll" data-layer-scroll>
          <TreeView.Root
            collection={collection}
            selectionMode="multiple"
            selectedValue={selectedDisplayIds}
            onSelectionChange={({ selectedValue }) => {
              if (suppressArkSelection.current) {
                suppressArkSelection.current = false;
                return;
              }
              store.selectLayers(selectedValue, "replace");
            }}
            onClickCapture={onClickCapture}
            onKeyDownCapture={onKeyDownCapture}
            className="ed-layer-tree"
          >
            <TreeView.Label className="ed-sr-only">Document layers</TreeView.Label>
            <TreeView.Tree>
              {collection.rootNode.children?.map((node, index) => renderRow(node, [index]))}
            </TreeView.Tree>
          </TreeView.Root>
        </div>
      )}
    </section>
  );
}
