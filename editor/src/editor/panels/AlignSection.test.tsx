import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EditorProvider, useEditorStore } from "../../editor-core/store";
import { AlignSection } from "./AlignSection";

function Harness({ ids }: { ids: string[] }) {
  const store = useEditorStore();
  const layers = store.state.doc.layers;
  return (
    <>
      <AlignSection layers={layers} selectedIds={ids} />
      <output data-testid="positions">
        {layers.map((layer) => `${layer.id}:${layer.transform.x}`).join("|")}
      </output>
    </>
  );
}

describe("AlignSection", () => {
  it("alignButtonsDisabledBelowTwo", () => {
    render(<EditorProvider><Harness ids={[]} /></EditorProvider>);
    expect(screen.getByRole("button", { name: "Align left" })).toBeDisabled();
  });

  it("distributeDisabledBelowThree", () => {
    render(<EditorProvider><Harness ids={["shape-star", "shape-orbit"]} /></EditorProvider>);
    expect(screen.getByRole("button", { name: "Distribute horizontally" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Align left" })).toBeEnabled();
  });

  it("alignLeftMovesSelectedLayers", () => {
    render(<EditorProvider><Harness ids={["shape-star", "shape-orbit"]} /></EditorProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Align left" }));
    const positions = screen.getByTestId("positions").textContent ?? "";
    expect(positions).not.toContain("shape-star:-7");
  });

  it("singleSelectionAlignsToArtboard", () => {
    render(<EditorProvider><Harness ids={["shape-star"]} /></EditorProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Align left" }));
    const text = screen.getByTestId("positions").textContent ?? "";
    expect(text).toContain("shape-star:");
  });
});
