import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Presentation } from "./Presentation";
import { TwinProvider } from "../twin/store";

describe("Presentation", () => {
  it("uses the canonical artboard without an editing selection", () => {
    render(
      <TwinProvider>
        <Presentation />
      </TwinProvider>,
    );

    const artboard = screen.getByTestId("presentation-artboard");
    expect(artboard).toHaveClass("ed-artboard");
    expect(artboard).toHaveAttribute("data-artboard-width", "765");
    expect(artboard).toHaveAttribute("data-artboard-height", "1000");
    expect(artboard.querySelector(".art-layer.is-selected")).toBeNull();
  });
});
