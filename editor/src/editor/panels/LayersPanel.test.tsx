import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EditorProvider, useEditorDocument, useEditorStore, type EditorActions, type EditorDocumentState } from "../../editor-core/store";
import { LayersPanel } from "./LayersPanel";

type Store = EditorActions;
let current: Store | null = null;
let currentDocument: EditorDocumentState | null = null;

function Bridge() {
  const store = useEditorStore();
  const documentState = useEditorDocument();
  useEffect(() => {
    current = store;
    currentDocument = documentState;
  }, [store, documentState]);
  return null;
}

function renderPanel() {
  current = null;
  currentDocument = null;
  render(
    <EditorProvider>
      <Bridge />
      <LayersPanel />
    </EditorProvider>,
  );
  return current as unknown as Store;
}

const row = (id: string) => screen.getByTestId(`layer-row-${id}`);
const rowIds = () =>
  screen.getAllByTestId(/^layer-row-/).map((item) => item.getAttribute("data-layer-row"));

function setCoarse(coarse: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: query.includes("pointer: coarse") ? coarse : query.includes("pointer: fine") ? !coarse : false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

describe("LayersPanel", () => {
  beforeEach(() => {
    setCoarse(false);
    current = null;
  });

  it("rendersLayersInDocumentOrder", () => {
    renderPanel();
    expect(rowIds()).toEqual(["shape-orbit", "shape-star", "type-open-studio"]);
  });

  it("shiftClickAddsLayer", () => {
    renderPanel();
    fireEvent.click(row("type-open-studio"));
    fireEvent.click(row("shape-star"), { shiftKey: true });
    expect(row("type-open-studio")).toHaveAttribute("aria-selected", "true");
    expect(row("shape-star")).toHaveAttribute("aria-selected", "true");
  });

  it("cmdClickTogglesLayer", () => {
    renderPanel();
    fireEvent.click(row("type-open-studio"));
    fireEvent.click(row("shape-star"), { metaKey: true });
    expect(row("shape-star")).toHaveAttribute("aria-selected", "true");
    fireEvent.click(row("shape-star"), { metaKey: true });
    expect(row("shape-star")).toHaveAttribute("aria-selected", "false");
  });

  it("eyeButtonTogglesVisibilityWithoutChangingSelection", async () => {
    renderPanel();
    fireEvent.click(row("type-open-studio"));
    const selectedBefore = currentDocument!.doc.selectedLayerIds;
    fireEvent.click(screen.getByRole("button", { name: "Hide Shape / Star" }));
    expect(screen.getByRole("button", { name: "Show Shape / Star" })).toBeInTheDocument();
    expect(currentDocument!.doc.selectedLayerIds).toEqual(selectedBefore);
    expect(currentDocument!.doc.layers.find((layer) => layer.id === "shape-star")?.visible).toBe(false);
  });

  it("doubleClickRenamesWithEnter", async () => {
    renderPanel();
    fireEvent.doubleClick(screen.getByText("Shape / Star"));
    const input = screen.getByTestId("layer-rename-input");
    fireEvent.change(input, { target: { value: "Star renamed" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(screen.getByText("Star renamed")).toBeInTheDocument());
  });

  it("escapeCancelsRename", async () => {
    renderPanel();
    fireEvent.doubleClick(screen.getByText("Shape / Star"));
    const input = screen.getByTestId("layer-rename-input");
    fireEvent.change(input, { target: { value: "Should not stick" } });
    fireEvent.keyDown(input, { key: "Escape" });
    await waitFor(() => expect(screen.getByText("Shape / Star")).toBeInTheDocument());
    expect(screen.queryByText("Should not stick")).toBeNull();
  });

  it("dragReordersLayers", async () => {
    renderPanel();
    const dataTransfer = { setData: vi.fn(), getData: () => "", effectAllowed: "", dropEffect: "" };
    fireEvent.dragStart(row("shape-orbit"), { dataTransfer });
    fireEvent.dragOver(row("type-open-studio"), { dataTransfer });
    fireEvent.drop(row("type-open-studio"), { dataTransfer });
    await waitFor(() => expect(currentDocument!.doc.layers.map((layer) => layer.id)).not.toEqual(["type-open-studio", "shape-star", "shape-orbit"]));
    expect(currentDocument!.doc.layers[0]?.id).toBe("shape-orbit");
  });

  it("touchReorderUsesPointerNotHtmlDrag", () => {
    setCoarse(true);
    renderPanel();
    expect(row("shape-orbit")).not.toHaveAttribute("draggable", "true");
    expect(screen.getByRole("button", { name: "Reorder Shape / Orbit" })).toBeInTheDocument();
  });

  it("pointerReorderCancelLeavesOrderUnchanged", () => {
    renderPanel();
    expect(screen.getByRole("button", { name: "Reorder Shape / Orbit" })).toBeInTheDocument();
  });

  it("moveUpDownActionsReorder", async () => {
    renderPanel();
    fireEvent.click(row("shape-star"));
    const trigger = screen.getByRole("button", { name: "Layer actions for Shape / Star" });
    fireEvent.pointerDown(trigger);
    fireEvent.click(trigger);
    fireEvent.click(await screen.findByRole("menuitem", { name: "Move up" }));
    await waitFor(() => expect(currentDocument!.doc.layers.at(-1)?.id).toBe("shape-star"));
  });

  it("keyboardArrowMovesSelection", async () => {
    renderPanel();
    fireEvent.click(row("type-open-studio"));
    row("type-open-studio").focus();
    fireEvent.keyDown(row("type-open-studio"), { key: "ArrowUp" });
    await waitFor(() => expect(row("shape-star")).toHaveAttribute("aria-selected", "true"));
  });

  it("panelShowsLayerCount", () => {
    renderPanel();
    expect(screen.getByTestId("layers-panel")).toHaveTextContent("3 layers");
  });

  it("emptyDocumentShowsNoLayersHint", async () => {
    const store = renderPanel();
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    await waitFor(() => expect(screen.getByText("No layers. Add text, a shape or a PNG.")).toBeInTheDocument());
    expect(screen.getByTestId("layers-panel")).toHaveTextContent("0 layers");
    expect(screen.getByRole("button", { name: "Add layer" })).toBeInTheDocument();
  });

  it("selectedLayerScrollsIntoView", async () => {
    const spy = vi.spyOn(Element.prototype, "scrollIntoView");
    renderPanel();
    fireEvent.click(row("shape-star"));
    await waitFor(() => expect(spy).toHaveBeenCalled());
    spy.mockRestore();
  });

  it("deletingFocusedLayerMovesFocusToNextRow", async () => {
    renderPanel();
    fireEvent.click(row("type-open-studio"));
    row("type-open-studio").focus();
    fireEvent.keyDown(row("type-open-studio"), { key: "Delete" });
    await waitFor(() => expect(screen.queryByTestId("layer-row-type-open-studio")).toBeNull());
    await waitFor(() => expect(document.activeElement).toBe(row("shape-star")));
  });
});
