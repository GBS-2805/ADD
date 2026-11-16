import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";
import { TwinProvider, useTwinStore } from "../../twin/store";
import { InspectorPanel } from "./InspectorPanel";

function Harness({ ids, mixed = false }: { ids: string[]; mixed?: boolean }) {
  const store = useTwinStore();
  const { selectLayers, setLayerOpacity } = store;
  useEffect(() => {
    selectLayers(ids, "replace");
    if (mixed && ids[0]) setLayerOpacity(ids[0], 0.5);
  }, [ids, mixed, selectLayers, setLayerOpacity]);
  return <InspectorPanel />;
}

describe("InspectorPanel", () => {
  it("noSelectionShowsDocument", async () => {
    render(<TwinProvider><Harness ids={[]} /></TwinProvider>);
    await waitFor(() => expect(screen.getByText("Artboard")).toBeInTheDocument());
    expect(screen.getByText("765 × 1000")).toBeInTheDocument();
    expect(screen.getByText("1530 × 2000")).toBeInTheDocument();
  });

  it("singleSelectionShowsTransform", async () => {
    render(<TwinProvider><Harness ids={["shape-star"]} /></TwinProvider>);
    await waitFor(() => expect(screen.getByTestId("transform-section")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Reference c" })).toHaveAttribute("aria-pressed", "true");
  });

  it("multiShowsBoundsAndAlign", async () => {
    render(<TwinProvider><Harness ids={["shape-star", "shape-orbit"]} /></TwinProvider>);
    await waitFor(() => expect(screen.getByText("2 layers")).toBeInTheDocument());
    expect(screen.getByTestId("align-section")).toBeInTheDocument();
    expect(screen.queryByTestId("transform-section")).toBeNull();
  });

  it("multiSelectShowsMixedOpacity", async () => {
    render(<TwinProvider><Harness ids={["shape-star", "shape-orbit"]} mixed /></TwinProvider>);
    await waitFor(() => expect(screen.getByText("Mixed")).toBeInTheDocument());
  });

  it("textContentEditedInInspector", async () => {
    render(<TwinProvider><Harness ids={["type-open-studio"]} /></TwinProvider>);
    await waitFor(() => expect(screen.getByLabelText("Text content")).toBeInTheDocument());
    const textarea = screen.getByLabelText("Text content");
    fireEvent.change(textarea, { target: { value: "HELLO" } });
    expect(textarea).toHaveValue("HELLO");
  });
});
