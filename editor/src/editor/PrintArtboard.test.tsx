import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PrintArtboard } from "./PrintArtboard";
import type { EditorDocument } from "../editor-core/types";

const documentWithLayers: EditorDocument = {
  background: { kind: "solid", hex: "#fafaf7" },
  selectedLayerIds: ["text"],
  layers: [
    {
      id: "text",
      name: "Text",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "PRINT", size: 40, align: "center", font: "instrument-serif" },
      fill: "#111111",
    },
  ],
};

describe("PrintArtboard", () => {
  it("printArtboardIsCanonicalAndReadOnly", () => {
    render(<PrintArtboard doc={documentWithLayers} />);
    const root = screen.getByTestId("print-root");
    const artboard = screen.getByTestId("print-artboard");
    expect(root).toHaveClass("ed-print-root");
    expect(artboard).toHaveAttribute("data-artboard-width", "765");
    expect(artboard).toHaveAttribute("data-artboard-height", "1000");
    expect(artboard).toHaveAttribute("data-artboard", "readonly");
    expect(artboard.querySelector(".is-selected")).toBeNull();
    expect(artboard.querySelector("[data-handle]")).toBeNull();
    expect(screen.queryByTestId("selection-overlay")).toBeNull();
  });

  it("printArtboardWorksWithZeroLayers", () => {
    render(<PrintArtboard doc={{ background: null, layers: [], selectedLayerIds: [] }} />);
    expect(screen.getByTestId("print-artboard")).toBeInTheDocument();
    expect(screen.getByTestId("print-artboard").querySelectorAll(".art-layer")).toHaveLength(0);
  });
});
