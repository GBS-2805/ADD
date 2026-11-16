import { useEffect, useRef } from "react";
import { useTwinStore } from "../twin/store";
import type { EditorTool } from "./types";

export interface EditorHotkeysOptions {
  enabled?: boolean;
  onPreview: () => void;
  onPalette: () => void;
  onEscape: () => void;
}

function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false;
  return Boolean(target.closest("input, textarea, select, [contenteditable='true']"));
}

function isBlockedSurface(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false;
  return Boolean(target.closest("[role='dialog'], [role='menu']"));
}

export function useEditorHotkeys({ enabled = true, onPreview, onPalette, onEscape }: EditorHotkeysOptions) {
  const store = useTwinStore();
  const previousTool = useRef<EditorTool | null>(null);
  const currentTool = useRef(store.state.tool);
  currentTool.current = store.state.tool;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!enabled || event.defaultPrevented || event.isComposing) return;
      if (isEditable(event.target) || isBlockedSurface(event.target)) return;

      const mod = event.metaKey || event.ctrlKey;
      const key = event.key.toLowerCase();

      if (mod && key === "k") {
        event.preventDefault();
        onPalette();
        return;
      }
      if (mod && key === "a") {
        event.preventDefault();
        store.selectAllLayers();
        return;
      }
      if (mod && key === "z") {
        event.preventDefault();
        if (event.shiftKey) store.redo();
        else store.undo();
        return;
      }
      if (mod && (key === "[" || key === "]")) {
        event.preventDefault();
        const selected = store.state.doc.selectedLayerIds;
        if (selected.length === 1) store.moveLayer(selected[0], key === "]" ? "up" : "down");
        return;
      }
      if (mod) return;

      if (key === "v" || key === "h" || key === "z") {
        event.preventDefault();
        store.setTool(key === "v" ? "move" : key === "h" ? "hand" : "zoom");
        return;
      }

      if (event.code === "Space" || key === " ") {
        event.preventDefault();
        if (!previousTool.current) previousTool.current = currentTool.current;
        store.setTool("hand");
        return;
      }

      if (key === "escape") {
        event.preventDefault();
        onEscape();
        return;
      }

      if ((key === "delete" || key === "backspace") && store.state.doc.selectedLayerIds.length > 0) {
        event.preventDefault();
        store.removeSelectedLayers();
        return;
      }

      const direction = key === "arrowleft" ? { x: -1, y: 0 }
        : key === "arrowright" ? { x: 1, y: 0 }
          : key === "arrowup" ? { x: 0, y: -1 }
            : key === "arrowdown" ? { x: 0, y: 1 }
              : null;
      if (!direction) return;
      const selected = store.state.doc.selectedLayerIds;
      if (selected.length === 0) return;
      event.preventDefault();
      const distance = event.shiftKey ? 10 : 1;
      const updates = Object.fromEntries(
        store.state.doc.layers
          .filter((layer) => selected.includes(layer.id))
          .map((layer) => [layer.id, {
            ...layer.transform,
            x: layer.transform.x + direction.x * distance,
            y: layer.transform.y + direction.y * distance,
          }]),
      );
      store.setLayerTransforms(updates);
    };

    const onKeyUp = (event: KeyboardEvent) => {
      if (event.code !== "Space" && event.key !== " ") return;
      if (previousTool.current) {
        store.setTool(previousTool.current);
        previousTool.current = null;
      }
    };

    const onBlur = () => {
      previousTool.current = null;
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", onBlur);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", onBlur);
    };
  }, [enabled, onEscape, onPalette, onPreview, store]);
}
