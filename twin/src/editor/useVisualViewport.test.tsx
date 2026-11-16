import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { useVisualViewport } from "./useVisualViewport";

function Probe() {
  const viewport = useVisualViewport();
  return (
    <output data-testid="probe">
      {viewport.height}:{viewport.offsetTop}:{viewport.keyboardOffset}
    </output>
  );
}

let listeners = new Map<string, () => void>();
const fakeViewport = { height: 500, offsetTop: 200 };

const installViewport = () => {
  listeners = new Map();
  Object.defineProperty(window, "visualViewport", {
    configurable: true,
    value: {
      get height() { return fakeViewport.height; },
      get offsetTop() { return fakeViewport.offsetTop; },
      addEventListener: (type: string, listener: () => void) => listeners.set(type, listener),
      removeEventListener: (type: string) => listeners.delete(type),
    },
  });
};

describe("useVisualViewport", () => {
  afterEach(() => {
    Object.defineProperty(window, "visualViewport", { configurable: true, value: undefined });
  });

  it("readsTheKeyboardInsetAndUpdatesOnVisualViewportResize", () => {
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 844 });
    installViewport();
    render(<Probe />);
    expect(screen.getByTestId("probe")).toHaveTextContent("500:200:144");

    fakeViewport.height = 400;
    fakeViewport.offsetTop = 140;
    act(() => listeners.get("resize")?.());
    expect(screen.getByTestId("probe")).toHaveTextContent("400:140:304");
  });
});
