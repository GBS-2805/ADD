import { useEffect, useMemo, useRef, useState } from "react";
import { Dialog, Portal } from "@ark-ui/react";
import { Search, X } from "lucide-react";
import { buildCommands } from "../editor-core/commands";
import { useEditorDocument, useEditorStore } from "../editor-core/store";
import type { EditorCommand } from "../editor-core/types";

export interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const store = useEditorStore();
  const documentState = useEditorDocument();
  const commands = useMemo(() => buildCommands(store, documentState), [store, documentState]);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const restoreFrame = useRef<number | null>(null);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return commands;
    return commands.filter((command) =>
      `${command.label} ${command.group} ${command.hint ?? ""}`.toLowerCase().includes(needle),
    );
  }, [commands, query]);

  const groups = useMemo(() => [...new Set(filtered.map((command) => command.group))], [filtered]);

  useEffect(() => {
    if (open) {
      previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const frame = window.requestAnimationFrame(() => inputRef.current?.focus());
      return () => window.cancelAnimationFrame(frame);
    }
    if (!previousFocus.current) return;
    const target = previousFocus.current;
    previousFocus.current = null;
    restoreFrame.current = window.requestAnimationFrame(() => {
      if (target.isConnected) target.focus();
    });
    return () => {
      if (restoreFrame.current !== null) window.cancelAnimationFrame(restoreFrame.current);
    };
  }, [open]);

  useEffect(() => {
    setActive(0);
  }, [query]);

  useEffect(() => {
    if (!open) {
      setQuery("");
      setActive(0);
    }
  }, [open]);

  const close = () => onOpenChange(false);

  const run = (command: EditorCommand | undefined) => {
    if (!command || command.disabled) return;
    command.run();
    close();
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close();
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => (filtered.length === 0 ? 0 : (index + 1) % filtered.length));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => (filtered.length === 0 ? 0 : (index - 1 + filtered.length) % filtered.length));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      run(filtered[active] ?? filtered[0]);
    }
  };

  if (!open) return null;

  return (
    <Dialog.Root open={open} onOpenChange={({ open: next }) => onOpenChange(next)}>
      <Portal>
        <Dialog.Backdrop className="ed-palette-backdrop" />
        <Dialog.Positioner className="ed-palette-positioner">
          <Dialog.Content
            className="ed-palette"
            data-testid="command-palette"
            onKeyDown={onKeyDown}
            aria-describedby="command-palette-description"
          >
            <Dialog.Title className="ed-palette-title">Actions</Dialog.Title>
            <Dialog.Description id="command-palette-description" className="ed-sr-only">
              Search and run an editor action.
            </Dialog.Description>
            <div className="ed-palette-search">
              <Search size={16} strokeWidth={1.7} aria-hidden="true" />
              <input
                ref={inputRef}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search actions"
                aria-label="Search actions"
                aria-controls="command-list"
                aria-activedescendant={filtered[active] ? `command-option-${filtered[active].id}` : undefined}
              />
              <button type="button" className="ed-palette-close" aria-label="Close actions" onClick={close}>
                <X size={15} strokeWidth={1.7} />
              </button>
            </div>
            <div className="ed-palette-list" id="command-list" role="listbox" aria-label="Actions">
              {groups.map((group) => (
                <div className="ed-palette-group" key={group}>
                  <div className="ed-palette-group-label" data-testid={`command-group-${group}`}>{group}</div>
                  {filtered.filter((command) => command.group === group).map((command) => {
                    const index = filtered.indexOf(command);
                    return (
                      <button
                        key={command.id}
                        id={`command-option-${command.id}`}
                        type="button"
                        role="option"
                        aria-selected={index === active}
                        className={`ed-palette-row ${index === active ? "is-active" : ""}`}
                        data-command-id={command.id}
                        data-command-active={index === active ? "true" : "false"}
                        disabled={command.disabled}
                        onMouseEnter={() => setActive(index)}
                        onClick={() => run(command)}
                      >
                        <span className="ed-palette-copy">
                          <strong>{command.label}</strong>
                          {command.hint ? <small>{command.hint}</small> : null}
                        </span>
                        {command.shortcut ? (
                          <kbd className="ed-palette-shortcut" data-testid={`command-shortcut-${command.id}`}>{command.shortcut}</kbd>
                        ) : null}
                      </button>
                    );
                  })}
                </div>
              ))}
              {filtered.length === 0 ? <p className="ed-palette-empty">No action matches “{query}”.</p> : null}
            </div>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  );
}
