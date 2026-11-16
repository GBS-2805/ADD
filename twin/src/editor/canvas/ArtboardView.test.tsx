import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { TwinDocument, TwinLayer } from "../../twin/types";
import { ArtboardView } from "./ArtboardView";

const textLayer: TwinLayer = {
  id: "headline",
  name: "Type / OPEN STUDIO",
  kind: "text",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 12 },
  visible: true,
  text: { content: "OPEN\nSTUDIO", size: 154, align: "center", font: "instrument-serif" },
  fill: "#111111",
};

const shapeLayer: TwinLayer = {
  id: "star",
  name: "Shape / Star",
  kind: "shape",
  effect: { kind: "solid", hex: "#dd4124" },
  blend: "normal",
  opacity: 1,
  transform: { x: 30, y: 40, scaleX: 2, scaleY: 0.5, rotation: 0 },
  visible: true,
  shape: { variant: "star" },
  fill: "#111111",
};

const makeDocument = (): TwinDocument => ({
  background: { kind: "solid", hex: "#fafaf7" },
  layers: [textLayer, shapeLayer],
  selectedLayerIds: ["headline"],
});

describe("ArtboardView", () => {
  it("renders at the canonical size", () => {
    render(<ArtboardView doc={makeDocument()} />);
    const artboard = screen.getByTestId("artboard");
    expect(artboard).toHaveAttribute("data-artboard-width", "765");
    expect(artboard).toHaveAttribute("data-artboard-height", "1000");
    expect(artboard).toHaveClass("ed-artboard");
  });

  it("renders each layer at its canonical frame", () => {
    render(<ArtboardView doc={makeDocument()} />);
    const headline = screen.getByTestId("art-layer-headline");
    expect(headline).toHaveAttribute("data-layer-id", "headline");
    expect(headline.style.width).toBe("642px");
    expect(parseFloat(headline.style.height)).toBeCloseTo(297.56, 5);
    expect(headline.style.left).toBe("-321px");
    expect(parseFloat(headline.style.top)).toBeCloseTo(-148.78, 5);
    expect(headline.style.transform).toBe("rotate(12deg)");
  });

  it("applies rotation around the frame center", () => {
    render(<ArtboardView doc={makeDocument()} />);
    const headline = screen.getByTestId("art-layer-headline");
    expect(headline.style.transformOrigin).toBe("center");
    expect(headline.style.transform).toBe("rotate(12deg)");
    expect(headline.style.left).toBe("-321px");
  });

  it("applies independent scaleX and scaleY", () => {
    render(<ArtboardView doc={makeDocument()} />);
    const star = screen.getByTestId("art-layer-star");
    expect(star.style.width).toBe("416px");
    expect(star.style.height).toBe("104px");
    expect(star.style.left).toBe("-178px");
    expect(star.style.top).toBe("-12px");
  });

  it("renders text and shape content", () => {
    render(<ArtboardView doc={makeDocument()} />);
    expect(screen.getByText("OPEN")).toBeInTheDocument();
    expect(screen.getByText("STUDIO")).toBeInTheDocument();
    expect(screen.getByTestId("art-layer-star").querySelector("svg")).not.toBeNull();
  });

  it("omits selection classes in read-only mode", () => {
    render(<ArtboardView doc={makeDocument()} readOnly />);
    expect(screen.getByTestId("art-layer-headline")).not.toHaveClass("is-selected");
  });

  it("clips effects to the layer frame", () => {
    render(<ArtboardView doc={makeDocument()} />);
    expect(screen.getByTestId("layer-effect-clip")).toHaveStyle({ overflow: "hidden" });
  });
});
