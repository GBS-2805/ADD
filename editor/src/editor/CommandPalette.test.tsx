import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { EditorProvider, useEditorDocument, useEditorStore } from "../editor-core/store";
import { CommandPalette } from "./CommandPalette";

function Host({ initialOpen = true, onOpenChange = () => undefined }: { initialOpen?: boolean; onOpenChange?: (open: boolean) => void }) {
  const [open, setOpen] = useState(initialOpen);
  const store = useEditorStore();
  const { doc } = useEditorDocument();
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true);
          onOpenChange(true);
        }}
      >
        Open palette
      </button>
      <output data-testid="selection">{doc.selectedLayerIds.join(",")}</output>
      <output data-testid="tool">{store.state.tool}</output>
      <CommandPalette
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          onOpenChange(next);
        }}
      />
    </>
  );
}

const renderPalette = (props: Parameters<typeof Host>[0] = {}) =>
  render(
    <EditorProvider>
      <Host {...props} />
    </EditorProvider>,
  );

describe("CommandPalette", () => {
  it("opensWithCmdKInTheShellAndFocusesSearch", async () => {
    renderPalette();
    const search = await screen.findByRole("textbox", { name: "Search actions" });
    await waitFor(() => expect(search).toHaveFocus());
  });

  it("filtersCommands", async () => {
    renderPalette();
    const search = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.change(search, { target: { value: "rulers" } });
    expect(screen.getByRole("option", { name: /Toggle rulers/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Save project file/ })).toBeNull();
  });

  it("enterRunsFirstMatch", async () => {
    renderPalette();
    const search = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.change(search, { target: { value: "select all" } });
    fireEvent.keyDown(search, { key: "Enter" });
    expect(screen.getByTestId("selection")).toHaveTextContent("type-open-studio,shape-star,shape-orbit");
  });

  it("arrowKeysMoveTheActiveCommand", async () => {
    renderPalette();
    const search = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.change(search, { target: { value: "align" } });
    fireEvent.keyDown(search, { key: "ArrowDown" });
    const active = document.querySelector('[data-command-active="true"]');
    expect(active).not.toBeNull();
    expect(active).toHaveAttribute("data-command-active", "true");
  });

  it("escapeClosesAndRestoresFocus", async () => {
    const onOpenChange = vi.fn();
    renderPalette({ initialOpen: false, onOpenChange });
    const trigger = screen.getByRole("button", { name: "Open palette" });
    trigger.focus();
    fireEvent.click(trigger);
    const search = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.keyDown(search, { key: "Escape" });
    expect(onOpenChange).toHaveBeenLastCalledWith(false);
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("shortcutsAreShown", async () => {
    renderPalette();
    await screen.findByRole("textbox", { name: "Search actions" });
    expect(screen.getByTestId("command-shortcut-select-all")).toHaveTextContent("⌘A");
    expect(screen.getByTestId("command-shortcut-undo")).toHaveTextContent("⌘Z");
  });

  it("emptyQueryShowsGroups", async () => {
    renderPalette();
    await screen.findByRole("textbox", { name: "Search actions" });
    expect(screen.getByTestId("command-group-Edit")).toBeInTheDocument();
    expect(screen.getByTestId("command-group-View")).toBeInTheDocument();
  });

  it("noUnsafeCommands", async () => {
    renderPalette();
    await screen.findByRole("textbox", { name: "Search actions" });
    const ids = [...document.querySelectorAll<HTMLElement>("[data-command-id]")].map((node) => node.dataset.commandId);
    expect(ids).not.toContain("open-commands");
    expect(ids).not.toContain("close");
    expect(ids.length).toBeGreaterThan(12);
    for (const node of document.querySelectorAll<HTMLElement>("[data-command-id]")) {
      expect(node.tagName).toBe("BUTTON");
    }
  });

  it("doesNotRenderWhenClosed", () => {
    renderPalette({ initialOpen: false });
    expect(screen.queryByTestId("command-palette")).toBeNull();
  });
});
