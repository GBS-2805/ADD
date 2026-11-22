import { readFileSync } from "node:fs";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SelectionOverlay } from "./SelectionOverlay";
import type { EditorLayer } from "../../editor-core/types";

const layer = (id: string, x: number, y: number, width = 100, height = 100, rotation = 0): EditorLayer => ({
  id,
  name: id,
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x, y, scaleX: width / 244, scaleY: height / 244, rotation },
  visible: true,
  shape: { variant: "block" },
  fill: "#111111",
});

describe("SelectionOverlay", () => {
  it("singleSelectionShowsEightHandlesAndRotation", () => {
    render(<SelectionOverlay layers={[layer("a", 100, 100)]} selectedIds={["a"]} />);
    expect(screen.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "1");
    for (const handle of ["nw", "n", "ne", "e", "se", "s", "sw", "w", "rotate"]) {
      expect(screen.getByTestId(`selection-overlay`).querySelector(`[data-handle="${handle}"]`)).toBeTruthy();
    }
  });

  it("handlesArePointerOnlyAndNotInTabOrder", () => {
    render(<SelectionOverlay layers={[layer("a", 100, 100)]} selectedIds={["a"]} />);
    const handles = screen.getByTestId("selection-overlay").querySelectorAll("[data-handle]");
    expect(handles).toHaveLength(9);
    for (const handle of handles) {
      expect(handle.getAttribute("tabindex")).toBe("-1");
      expect(handle.getAttribute("role")).toBe("presentation");
      expect(handle.getAttribute("aria-hidden")).toBe("true");
    }
  });

  it("multiSelectionShowsDashedUnionWithoutHandles", () => {
    render(
      <SelectionOverlay
        layers={[layer("a", 100, 100), layer("b", 300, 260)]}
        selectedIds={["a", "b"]}
      />,
    );
    const overlay = screen.getByTestId("selection-overlay");
    expect(overlay).toHaveAttribute("data-selection-count", "2");
    expect(overlay.querySelector(".ed-selection-multi")).toBeTruthy();
    expect(overlay.querySelector("[data-handle]")).toBeNull();
    expect(screen.getByText("2 layers")).toBeInTheDocument();
  });

  it("rendersSnapGuidesAndMarquee", () => {
    const { container } = render(
      <SelectionOverlay
        layers={[layer("a", 100, 100)]}
        selectedIds={["a"]}
        guides={[{ axis: "x", position: 382.5 }]}
        marquee={{ x: 10, y: 20, width: 100, height: 80 }}
      />,
    );
    expect(container.querySelector('[data-guide-axis="x"]')).toBeTruthy();
    expect(screen.getByTestId("marquee")).toBeInTheDocument();
  });

  it("handleHitAreaIs44OnCoarsePointer", () => {
    const css = readFileSync("src/editor/editor.css", "utf8");
    expect(css).toContain("@media (pointer: coarse)");
    expect(css).toContain("calc(44px / var(--zoom))");
  });

  it("rotatedSelectionOutlineUsesFrameRotation", () => {
    render(<SelectionOverlay layers={[layer("a", 100, 100, 100, 100, 37)]} selectedIds={["a"]} />);
    const box = screen.getByTestId("selection-overlay").querySelector(".ed-selection-single") as HTMLElement;
    expect(box.style.transform).toContain("rotate(37deg)");
  });
});
