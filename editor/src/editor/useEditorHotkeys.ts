import { useEffect, useRef } from "react";
import { useEditorStore } from "../editor-core/store";
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
  const store = useEditorStore();
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
        store.moveSelectedLayer(key === "]" ? "up" : "down");
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

      if (key === "delete" || key === "backspace") {
        if (store.deleteSelection()) event.preventDefault();
        return;
      }

      const direction = key === "arrowleft" ? { x: -1, y: 0 }
        : key === "arrowright" ? { x: 1, y: 0 }
          : key === "arrowup" ? { x: 0, y: -1 }
            : key === "arrowdown" ? { x: 0, y: 1 }
              : null;
      if (!direction) return;
      const distance = event.shiftKey ? 10 : 1;
      if (store.nudgeSelection(direction.x * distance, direction.y * distance)) event.preventDefault();
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
