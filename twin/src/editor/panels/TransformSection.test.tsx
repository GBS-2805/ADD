import { readFileSync } from "node:fs";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwinProvider, useTwinStore } from "../../twin/store";
import { NumericField } from "./NumericField";
import { TransformSection } from "./TransformSection";
import { layerFrame } from "../model/geometry";

function Selected({ id = "shape-star" }: { id?: string }) {
  const store = useTwinStore();
  const layer = store.state.doc.layers.find((item) => item.id === id);
  if (!layer) return null;
  const frame = layerFrame(layer);
  return (
    <>
      <TransformSection layer={layer} />
      <output data-testid="frame">{`${frame.width},${frame.height},${frame.x},${frame.y},${frame.rotation}`}</output>
    </>
  );
}

const value = (label: string) => (screen.getByTestId(`numeric-${label}`) as HTMLInputElement).value;
const commit = async (label: string, next: number) => {
  const input = screen.getByTestId(`numeric-${label}`);
  fireEvent.input(input, { target: { value: String(next) } });
  fireEvent.blur(input);
  await waitFor(() => expect(input).not.toHaveFocus());
};

describe("TransformSection", () => {
  it("referencePointChangesXWithFixedAnchor", async () => {
    render(<TwinProvider><Selected /></TwinProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Reference nw" }));
    const before = Number(value("x"));
    await commit("x", 120);
    await waitFor(() => expect(Number(value("x"))).toBeCloseTo(120));
    const width = Number(value("w"));
    expect(Number(value("x"))).toBeCloseTo(120);
    expect(width).toBeGreaterThan(0);
    expect(before).not.toBe(120);
  });

  it("widthChangeKeepsReferencePoint", async () => {
    render(<TwinProvider><Selected /></TwinProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Reference nw" }));
    const x = value("x");
    const y = value("y");
    await commit("w", 300);
    await waitFor(() => expect(screen.getByTestId("frame").textContent).toContain("300"));
    expect(value("x")).toBe(x);
    expect(value("y")).toBe(y);
  });

  it("aspectLockChangesBothAxes", async () => {
    render(<TwinProvider><Selected /></TwinProvider>);
    const before = screen.getByTestId("frame").textContent!.split(",").map(Number);
    fireEvent.click(screen.getByRole("switch", { name: "Lock aspect ratio" }));
    await commit("w", before[0] * 2);
    await waitFor(() => expect(Number(value("w"))).toBeCloseTo(before[0] * 2, 0));
    expect(Number(value("h"))).toBeCloseTo(before[1] * 2, 0);
  });

  it("rotationFieldKeepsReferencePoint", async () => {
    render(<TwinProvider><Selected /></TwinProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Reference sw" }));
    const x = value("x");
    const y = value("y");
    await commit("rotation", 30);
    await waitFor(() => expect(Number(value("rotation"))).toBeCloseTo(30));
    expect(value("x")).toBe(x);
    expect(value("y")).toBe(y);
  });

  it("numericFieldCommitsOnce", async () => {
    const onCommit = vi.fn();
    render(<NumericField label="Test" value={5} onCommit={onCommit} />);
    const input = screen.getByTestId("numeric-test");
    fireEvent.input(input, { target: { value: "12" } });
    fireEvent.blur(input);
    await waitFor(() => expect(onCommit).toHaveBeenCalled());
    expect(onCommit).toHaveBeenCalledTimes(1);
    expect(onCommit).toHaveBeenCalledWith(12);
  });

  it("numericFieldsUseDecimalInputMode", () => {
    render(<TwinProvider><Selected /></TwinProvider>);
    expect(screen.getByTestId("numeric-x")).toHaveAttribute("inputmode", "decimal");
  });

  it("numericFieldsAre44OnPhone", () => {
    const css = readFileSync("src/editor/editor.css", "utf8");
    expect(css).toContain("@media (max-width: 600px)");
    expect(css).toContain("height: 44px");
    expect(css).toContain("font-size: 16px");
  });
});
