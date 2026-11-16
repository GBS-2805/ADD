import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TwinProvider, useTwinStore } from "../../twin/store";
import { AlignSection } from "./AlignSection";

function Harness({ ids }: { ids: string[] }) {
  const store = useTwinStore();
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
    render(<TwinProvider><Harness ids={[]} /></TwinProvider>);
    expect(screen.getByRole("button", { name: "Align left" })).toBeDisabled();
  });

  it("distributeDisabledBelowThree", () => {
    render(<TwinProvider><Harness ids={["shape-star", "shape-orbit"]} /></TwinProvider>);
    expect(screen.getByRole("button", { name: "Distribute horizontally" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Align left" })).toBeEnabled();
  });

  it("alignLeftMovesSelectedLayers", () => {
    render(<TwinProvider><Harness ids={["shape-star", "shape-orbit"]} /></TwinProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Align left" }));
    const positions = screen.getByTestId("positions").textContent ?? "";
    expect(positions).not.toContain("shape-star:-7");
  });

  it("singleSelectionAlignsToArtboard", () => {
    render(<TwinProvider><Harness ids={["shape-star"]} /></TwinProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Align left" }));
    const text = screen.getByTestId("positions").textContent ?? "";
    expect(text).toContain("shape-star:");
  });
});
