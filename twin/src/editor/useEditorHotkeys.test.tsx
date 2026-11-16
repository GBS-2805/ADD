import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwinProvider, useTwinStore } from "../twin/store";
import { useEditorHotkeys } from "./useEditorHotkeys";

function Harness({
  onPreview = () => undefined,
  onPalette = () => undefined,
  onEscape = () => undefined,
  enabled = true,
}: {
  onPreview?: () => void;
  onPalette?: () => void;
  onEscape?: () => void;
  enabled?: boolean;
}) {
  const store = useTwinStore();
  useEditorHotkeys({ enabled, onPreview, onPalette, onEscape });
  const selected = store.state.doc.selectedLayerIds;
  return (
    <>
      <output data-testid="tool">{store.state.tool}</output>
      <output data-testid="selection">{selected.join(",")}</output>
      <output data-testid="order">{store.state.doc.layers.map((layer) => layer.id).join(",")}</output>
      <output data-testid="transform">
        {JSON.stringify(store.state.doc.layers.find((layer) => layer.id === "type-open-studio")?.transform)}
      </output>
      <input aria-label="Editable" />
      <div role="dialog" aria-label="Dialog">
        <button type="button">Dialog action</button>
      </div>
      <div role="menu" aria-label="Menu">
        <button type="button" role="menuitem">Menu action</button>
      </div>
    </>
  );
}

const renderHarness = (props: Parameters<typeof Harness>[0] = {}) =>
  render(
    <TwinProvider>
      <Harness {...props} />
    </TwinProvider>,
  );

describe("useEditorHotkeys", () => {
  it("vSelectsMoveTool", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "h" });
    expect(screen.getByTestId("tool")).toHaveTextContent("hand");
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "v" });
    expect(screen.getByTestId("tool")).toHaveTextContent("move");
  });

  it("hSelectsHand", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "h" });
    expect(screen.getByTestId("tool")).toHaveTextContent("hand");
  });

  it("zSelectsZoom", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "z" });
    expect(screen.getByTestId("tool")).toHaveTextContent("zoom");
  });

  it("spaceTemporarilyPans", () => {
    renderHarness();
    fireEvent.keyDown(window, { key: " ", code: "Space" });
    expect(screen.getByTestId("tool")).toHaveTextContent("hand");
    fireEvent.keyUp(window, { key: " ", code: "Space" });
    expect(screen.getByTestId("tool")).toHaveTextContent("move");
  });

  it("cmdAUsesSelectAll", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "a", metaKey: true });
    expect(screen.getByTestId("selection")).toHaveTextContent("type-open-studio,shape-star,shape-orbit");
  });

  it("escapeCallsTheEscapeOwner", () => {
    const onEscape = vi.fn();
    renderHarness({ onEscape });
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "Escape" });
    expect(onEscape).toHaveBeenCalledTimes(1);
  });

  it("deleteRemovesSelected", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "Delete" });
    expect(screen.getByTestId("order")).toHaveTextContent("shape-star,shape-orbit");
    expect(screen.getByTestId("selection")).toHaveTextContent("shape-star");
  });

  it("arrowsNudgeOne", () => {
    renderHarness();
    const before = JSON.parse(screen.getByTestId("transform").textContent ?? "{}") as { x: number; y: number };
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "ArrowRight" });
    const after = JSON.parse(screen.getByTestId("transform").textContent ?? "{}") as { x: number; y: number };
    expect(after.x - before.x).toBe(1);
  });

  it("shiftArrowsNudgeTen", () => {
    renderHarness();
    const before = JSON.parse(screen.getByTestId("transform").textContent ?? "{}") as { x: number; y: number };
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "ArrowDown", shiftKey: true });
    const after = JSON.parse(screen.getByTestId("transform").textContent ?? "{}") as { x: number; y: number };
    expect(after.y - before.y).toBe(10);
  });

  it("cmdBracketReorders", () => {
    renderHarness();
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "]", metaKey: true });
    expect(screen.getByTestId("order")).toHaveTextContent("shape-star,type-open-studio,shape-orbit");
  });

  it("cmdKUsesThePaletteOwner", () => {
    const onPalette = vi.fn();
    renderHarness({ onPalette });
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "k", metaKey: true });
    expect(onPalette).toHaveBeenCalledTimes(1);
  });

  it("shortcutsIgnoredInInput", () => {
    renderHarness();
    const input = screen.getByLabelText("Editable");
    input.focus();
    fireEvent.keyDown(input, { key: "h" });
    expect(screen.getByTestId("tool")).toHaveTextContent("move");
  });

  it("shortcutsIgnoredInDialog", () => {
    renderHarness();
    const button = screen.getByRole("button", { name: "Dialog action" });
    button.focus();
    fireEvent.keyDown(button, { key: "h" });
    expect(screen.getByTestId("tool")).toHaveTextContent("move");
  });

  it("shortcutsIgnoredInMenu", () => {
    renderHarness();
    const button = screen.getByRole("menuitem", { name: "Menu action" });
    button.focus();
    fireEvent.keyDown(button, { key: "h" });
    expect(screen.getByTestId("tool")).toHaveTextContent("move");
  });

  it("ignoresDisabledAndComposingEvents", () => {
    const onPalette = vi.fn();
    const { rerender } = render(
      <TwinProvider>
        <Harness onPalette={onPalette} enabled={false} />
      </TwinProvider>,
    );
    fireEvent.keyDown(screen.getByTestId("tool"), { key: "k", metaKey: true });
    expect(onPalette).not.toHaveBeenCalled();
    rerender(
      <TwinProvider>
        <Harness onPalette={onPalette} />
      </TwinProvider>,
    );
    const event = new KeyboardEvent("keydown", { key: "k", metaKey: true, isComposing: true, bubbles: true });
    window.dispatchEvent(event);
    expect(onPalette).not.toHaveBeenCalled();
  });
});
