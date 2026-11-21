import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { EditorProvider, useEditorStore } from "../editor-core/store";
import { PreviewMode } from "./PreviewMode";

function Bridge() {
  const store = useEditorStore();
  return <output data-testid="document">{JSON.stringify(store.state.doc)}</output>;
}

function PreviewHost() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Open preview</button>
      <Bridge />
      {open ? <PreviewMode layout="compact" onLayoutChange={() => undefined} onExit={() => setOpen(false)} /> : null}
    </>
  );
}

describe("PreviewMode", () => {
  it("previewShowsCompactDevice", () => {
    render(
      <EditorProvider>
        <PreviewMode layout="compact" onLayoutChange={() => undefined} onExit={() => undefined} />
      </EditorProvider>,
    );
    expect(screen.getByTestId("preview-mode")).toHaveAttribute("data-layout", "compact");
    expect(screen.getByTestId("device-frame-compact")).toBeInTheDocument();
    expect(screen.getByTestId("preview-artboard")).toHaveAttribute("data-artboard-width", "765");
    expect(screen.getByTestId("preview-artboard")).toHaveAttribute("data-artboard-height", "1000");
    expect(screen.getByTestId("preview-artboard")).toHaveAttribute("data-artboard", "readonly");
  });

  it("previewShowsExpandedDevice", () => {
    render(
      <EditorProvider>
        <PreviewMode layout="expanded" onLayoutChange={() => undefined} onExit={() => undefined} />
      </EditorProvider>,
    );
    expect(screen.getByTestId("device-frame-expanded")).toBeInTheDocument();
    expect(screen.getByTestId("preview-mode")).toHaveAttribute("data-layout", "expanded");
  });

  it("segmentGroupSwitchesDevice", async () => {
    const onLayoutChange = vi.fn();
    render(
      <EditorProvider>
        <PreviewMode layout="compact" onLayoutChange={onLayoutChange} onExit={() => undefined} />
      </EditorProvider>,
    );
    const radio = screen.getByRole("radio", { name: "Expanded 840" }) as HTMLInputElement;
    fireEvent.click(radio);
    await waitFor(() => expect(onLayoutChange).toHaveBeenCalledWith("expanded"));
  });

  it("previewHasNoSelectionHandles", () => {
    render(
      <EditorProvider>
        <PreviewMode layout="compact" onLayoutChange={() => undefined} onExit={() => undefined} />
      </EditorProvider>,
    );
    expect(document.querySelector("[data-handle]")).toBeNull();
    expect(screen.queryByTestId("selection-overlay")).toBeNull();
    expect(screen.queryByRole("button", { name: /Save project/ })).toBeNull();
  });

  it("escapeExitsPreview", () => {
    const onExit = vi.fn();
    render(
      <EditorProvider>
        <PreviewMode layout="compact" onLayoutChange={() => undefined} onExit={onExit} />
      </EditorProvider>,
    );
    fireEvent.keyDown(screen.getByTestId("preview-mode"), { key: "Escape" });
    expect(onExit).toHaveBeenCalledTimes(1);
  });

  it("previewDoesNotChangeDocument", () => {
    render(
      <EditorProvider>
        <PreviewMode layout="compact" onLayoutChange={() => undefined} onExit={() => undefined} />
      </EditorProvider>,
    );
    const before = document.querySelector('[data-testid="preview-artboard"]')?.outerHTML;
    fireEvent.click(screen.getByTestId("preview-artboard"));
    fireEvent.keyDown(screen.getByTestId("preview-mode"), { key: "ArrowRight" });
    expect(document.querySelector('[data-testid="preview-artboard"]')?.outerHTML).toBe(before);
  });

  it("previewExitRestoresFocus", async () => {
    render(
      <EditorProvider>
        <PreviewHost />
      </EditorProvider>,
    );
    const trigger = screen.getByRole("button", { name: "Open preview" });
    trigger.focus();
    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("button", { name: "Exit preview" }));
    await waitFor(() => expect(trigger).toHaveFocus());
  });
});
