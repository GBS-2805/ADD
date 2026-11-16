import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import App from "../App";

const setViewport = (width: number, height: number) => {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: width });
  Object.defineProperty(window, "innerHeight", { configurable: true, value: height });
  window.dispatchEvent(new Event("resize"));
};

const setSegments = (segments: { x: number; y: number; width: number; height: number }[] | undefined) => {
  Object.defineProperty(window, "viewport", { configurable: true, value: segments ? { segments } : undefined });
};

const setPosture = (type: "folded" | "continuous" | undefined) => {
  Object.defineProperty(navigator, "devicePosture", { configurable: true, value: type ? { type } : undefined });
};

const renderWorkbench = (width: number, height: number) => {
  setViewport(width, height);
  window.history.replaceState({}, "", "/");
  return render(<App />);
};

describe("EditorShell device matrix", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/");
    setSegments(undefined);
    setPosture(undefined);
  });

  afterEach(() => {
    setSegments(undefined);
    setPosture(undefined);
  });

  it("desktopUsesSplitterPanels", () => {
    renderWorkbench(1440, 900);
    expect(screen.getByTestId("editor-splitter")).toBeInTheDocument();
  });

  it("coverUsesQuickMode", () => {
    renderWorkbench(349, 316);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "cover");
    expect(screen.getByTestId("cover-shell")).toBeInTheDocument();
    expect(screen.getByTestId("cover-quick-bar")).toBeInTheDocument();
  });

  it("coverHasFive44pxActions", () => {
    renderWorkbench(349, 316);
    const bar = screen.getByTestId("cover-quick-bar");
    const buttons = Array.from(bar.querySelectorAll("button"));
    expect(buttons).toHaveLength(5);
    for (const button of buttons) {
      expect(parseFloat(button.style.minWidth)).toBeGreaterThanOrEqual(44);
      expect(parseFloat(button.style.minHeight)).toBeGreaterThanOrEqual(44);
    }
  });

  it("coverHidesInspectorRulersAndSplitter", () => {
    renderWorkbench(320, 320);
    expect(screen.queryByTestId("editor-splitter")).toBeNull();
    expect(screen.queryByTestId("inspector-panel")).toBeNull();
    expect(screen.queryByTestId("ruler-x")).toBeNull();
  });

  it("phoneUsesBottomDock", () => {
    renderWorkbench(390, 844);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "phone");
    expect(screen.getByTestId("phone-shell")).toBeInTheDocument();
    expect(screen.getByTestId("phone-tool-dock")).toBeInTheDocument();
  });

  it("phoneInspectorIsBottomSheet", () => {
    renderWorkbench(390, 844);
    fireEvent.click(screen.getByRole("button", { name: "Inspector" }));
    expect(screen.getByTestId("bottom-sheet-inspector")).toBeInTheDocument();
    expect(screen.getByTestId("bottom-sheet-inspector")).toHaveAttribute("data-placement", "bottom");
    expect(screen.getByTestId("bottom-sheet-inspector")).toHaveAttribute("data-full-height", "false");
  });

  it("phoneLayersSheetIsFullHeight", () => {
    renderWorkbench(390, 844);
    fireEvent.click(screen.getByRole("button", { name: "Layers" }));
    expect(screen.getByTestId("bottom-sheet-layers")).toHaveAttribute("data-full-height", "true");
  });

  it("phoneUses100dvhAndSafeArea", () => {
    renderWorkbench(390, 844);
    const shell = screen.getByTestId("phone-shell");
    expect(shell).toHaveAttribute("data-safe-area", "true");
    expect(shell.style.height).toBe("100dvh");
  });

  it("drawerReturnsFocusToTrigger", async () => {
    renderWorkbench(390, 844);
    const trigger = screen.getByRole("button", { name: "Layers" });
    trigger.focus();
    fireEvent.click(trigger);
    fireEvent.click(await screen.findByRole("button", { name: "Close Layers" }));
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("tabletUsesDrawers", () => {
    renderWorkbench(900, 900);
    expect(screen.getByTestId("tablet-shell")).toBeInTheDocument();
    expect(screen.queryByTestId("editor-splitter")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Open layers" }));
    expect(screen.getByTestId("bottom-sheet-layers")).toBeInTheDocument();
  });

  it("landscapeUsesSideRail", () => {
    renderWorkbench(840, 360);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "phone");
    expect(screen.getByTestId("phone-shell")).toHaveAttribute("data-orientation", "landscape");
    expect(screen.getByTestId("phone-tool-dock")).toHaveClass("is-side-rail");
  });

  it("foldUsesTopCanvasBottomTools", () => {
    setSegments([
      { x: 0, y: 0, width: 360, height: 420 },
      { x: 0, y: 420, width: 360, height: 420 },
    ]);
    renderWorkbench(360, 840);
    expect(screen.getByTestId("fold-shell")).toHaveAttribute("data-fold", "vertical");
    expect(screen.getByTestId("fold-canvas")).toBeInTheDocument();
    expect(screen.getByTestId("fold-tool-deck")).toBeInTheDocument();
  });

  it("foldGutterAvoidsContent", () => {
    setSegments([
      { x: 0, y: 0, width: 360, height: 400 },
      { x: 0, y: 420, width: 360, height: 400 },
    ]);
    renderWorkbench(360, 840);
    expect(screen.getByTestId("fold-gutter").children).toHaveLength(0);
  });

  it("foldFallsBackToBottomSheetWithoutSegments", () => {
    setPosture("folded");
    renderWorkbench(390, 844);
    expect(screen.getByTestId("phone-shell")).toBeInTheDocument();
    expect(screen.queryByTestId("fold-shell")).toBeNull();
  });

  it("foldSegmentsWinOverPosture", () => {
    setPosture("folded");
    setSegments([
      { x: 0, y: 0, width: 360, height: 420 },
      { x: 0, y: 420, width: 360, height: 420 },
    ]);
    renderWorkbench(360, 840);
    expect(screen.getByTestId("fold-shell")).toBeInTheDocument();
  });

  it("visualViewportKeepsFocusedFieldAboveKeyboard", () => {
    Object.defineProperty(window, "visualViewport", {
      configurable: true,
      value: { height: 500, offsetTop: 200, addEventListener: () => undefined, removeEventListener: () => undefined },
    });
    renderWorkbench(390, 844);
    fireEvent.click(screen.getByRole("button", { name: "Inspector" }));
    const sheet = screen.getByTestId("bottom-sheet-inspector");
    expect(sheet).toHaveAttribute("data-visual-height", "500");
    expect(sheet).toHaveAttribute("data-visual-offset", "200");
  });
});
