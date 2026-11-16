import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import App from "../App";

const setViewport = (width: number, height: number) => {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: width });
  Object.defineProperty(window, "innerHeight", { configurable: true, value: height });
  window.dispatchEvent(new Event("resize"));
};

const renderWorkbench = (width = 1440, height = 900) => {
  setViewport(width, height);
  window.history.replaceState({}, "", "/");
  return render(<App />);
};

describe("EditorShell focus ownership", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("canvasIsLabeledAndFocusable", () => {
    renderWorkbench();
    const canvas = screen.getByRole("region", { name: "Artboard canvas" });
    expect(canvas).toHaveAttribute("tabindex", "0");
    canvas.focus();
    expect(canvas).toHaveFocus();
  });

  it("statusLiveRegionAnnouncesSelectionCount", async () => {
    renderWorkbench();
    const canvas = screen.getByRole("region", { name: "Artboard canvas" });
    canvas.focus();
    fireEvent.keyDown(canvas, { key: "a", code: "KeyA", metaKey: true });
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("3 layers selected. Zoom 100%."),
    );
  });

  it("handlesAreNotInTabOrder", () => {
    renderWorkbench();
    const handles = document.querySelectorAll("[data-handle]");
    expect(handles).toHaveLength(9);
    for (const handle of handles) {
      expect(handle).toHaveAttribute("tabindex", "-1");
      expect(handle).toHaveAttribute("role", "presentation");
    }
  });

  it("paletteReturnsFocusToTrigger", async () => {
    renderWorkbench();
    const canvas = screen.getByRole("region", { name: "Artboard canvas" });
    canvas.focus();
    fireEvent.keyDown(canvas, { key: "k", code: "KeyK", metaKey: true });
    const input = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.keyDown(input, { key: "Escape" });
    await waitFor(() => expect(screen.queryByTestId("command-palette")).toBeNull());
    await waitFor(() => expect(canvas).toHaveFocus());
  });

  it("previewReturnsFocusToPreviewButton", async () => {
    renderWorkbench();
    const trigger = screen.getByTestId("preview-trigger");
    trigger.focus();
    fireEvent.click(trigger);
    await screen.findByTestId("preview-mode");
    fireEvent.click(screen.getByRole("button", { name: "Exit preview" }));
    await waitFor(() => expect(screen.queryByTestId("preview-mode")).toBeNull());
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("escapeOrderPaletteThenPreviewThenDrawers", async () => {
    renderWorkbench(390, 844);
    const canvas = screen.getByRole("region", { name: "Artboard canvas" });
    canvas.focus();

    fireEvent.keyDown(canvas, { key: "k", code: "KeyK", metaKey: true });
    const input = await screen.findByRole("textbox", { name: "Search actions" });
    fireEvent.keyDown(input, { key: "Escape" });
    await waitFor(() => expect(screen.queryByTestId("command-palette")).toBeNull());

    const previewTrigger = screen.getByTestId("preview-trigger");
    previewTrigger.focus();
    fireEvent.click(previewTrigger);
    const preview = await screen.findByTestId("preview-mode");
    fireEvent.keyDown(preview, { key: "Escape" });
    await waitFor(() => expect(screen.queryByTestId("preview-mode")).toBeNull());

    fireEvent.click(screen.getByRole("button", { name: "Layers" }));
    const sheet = await screen.findByTestId("bottom-sheet-layers");
    const close = sheet.querySelector("button") as HTMLButtonElement;
    close.focus();
    fireEvent.keyDown(close, { key: "Escape" });
    await waitFor(() => expect(screen.getByTestId("bottom-sheet-layers")).not.toBeVisible());

    canvas.focus();
    fireEvent.keyDown(canvas, { key: "Escape" });
    await waitFor(() => expect(document.querySelectorAll(".ed-layer.is-selected")).toHaveLength(0));
  });
});
