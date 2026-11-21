import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";
import { EditorProvider, useEditorStore } from "../../editor-core/store";
import { InspectorPanel } from "./InspectorPanel";

function Harness({ ids, mixed = false }: { ids: string[]; mixed?: boolean }) {
  const store = useEditorStore();
  const { selectLayers, setLayerOpacity } = store;
  useEffect(() => {
    selectLayers(ids, "replace");
    if (mixed && ids[0]) setLayerOpacity(ids[0], 0.5);
  }, [ids, mixed, selectLayers, setLayerOpacity]);
  return <InspectorPanel />;
}

describe("InspectorPanel", () => {
  it("noSelectionShowsDocument", async () => {
    render(<EditorProvider><Harness ids={[]} /></EditorProvider>);
    await waitFor(() => expect(screen.getByText("Artboard")).toBeInTheDocument());
    expect(screen.getByText("765 × 1000")).toBeInTheDocument();
    expect(screen.getByText("1530 × 2000")).toBeInTheDocument();
  });

  it("singleSelectionShowsTransform", async () => {
    render(<EditorProvider><Harness ids={["shape-star"]} /></EditorProvider>);
    await waitFor(() => expect(screen.getByTestId("transform-section")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Reference c" })).toHaveAttribute("aria-pressed", "true");
  });

  it("multiShowsBoundsAndAlign", async () => {
    render(<EditorProvider><Harness ids={["shape-star", "shape-orbit"]} /></EditorProvider>);
    await waitFor(() => expect(screen.getByText("2 layers")).toBeInTheDocument());
    expect(screen.getByTestId("align-section")).toBeInTheDocument();
    expect(screen.queryByTestId("transform-section")).toBeNull();
  });

  it("multiSelectShowsMixedOpacity", async () => {
    render(<EditorProvider><Harness ids={["shape-star", "shape-orbit"]} mixed /></EditorProvider>);
    await waitFor(() => expect(screen.getByText("Mixed")).toBeInTheDocument());
  });

  it("textContentEditedInInspector", async () => {
    render(<EditorProvider><Harness ids={["type-open-studio"]} /></EditorProvider>);
    await waitFor(() => expect(screen.getByLabelText("Text content")).toBeInTheDocument());
    const textarea = screen.getByLabelText("Text content");
    fireEvent.change(textarea, { target: { value: "HELLO" } });
    expect(textarea).toHaveValue("HELLO");
  });
});
