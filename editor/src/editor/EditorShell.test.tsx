import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import App from "../App";

const setViewport = (width: number, height: number) => {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: width });
  Object.defineProperty(window, "innerHeight", { configurable: true, value: height });
  window.dispatchEvent(new Event("resize"));
};

const renderWorkbench = (width = 1440, height = 900) => {
  setViewport(width, height);
  window.history.pushState({}, "", "/");
  return render(<App />);
};

describe("EditorShell", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("defaultRouteContainsLayersInspectorAndCanvas", () => {
    renderWorkbench();
    expect(screen.getByTestId("editor-shell")).toBeInTheDocument();
    expect(screen.getByTestId("layers-panel")).toBeInTheDocument();
    expect(screen.getByTestId("inspector-panel")).toBeInTheDocument();
    expect(screen.getByTestId("canvas")).toBeInTheDocument();
  });

  it("editorHasNoDeviceFrame", () => {
    const { container } = renderWorkbench();
    expect(container.querySelector(".device-frame")).toBeNull();
    expect(screen.queryByTestId("stage")).toBeNull();
  });

  it("desktopUsesSplitterPanels", () => {
    renderWorkbench();
    expect(screen.getByTestId("editor-splitter")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^splitter-panel-/)).toHaveLength(3);
  });

  it("splitterCollapsesToRail", () => {
    renderWorkbench();
    fireEvent.click(screen.getByRole("button", { name: "Collapse layers" }));
    expect(screen.getByTestId("layers-panel")).toHaveAttribute("data-collapsed", "true");
    expect(screen.getByRole("button", { name: "Expand layers" })).toHaveAttribute("aria-expanded", "false");
  });

  it("splitterKeyboardResizes", async () => {
    renderWorkbench();
    const splitter = screen.getByTestId("editor-splitter");
    const before = splitter.getAttribute("data-sizes");
    const trigger = screen.getByRole("separator", { name: "Resize layers and canvas" });
    trigger.focus();
    fireEvent.keyDown(trigger, { key: "ArrowRight" });
    await waitFor(() => expect(splitter.getAttribute("data-sizes")).not.toBe(before));
  });

  it("breakpointSelectsTablet", () => {
    renderWorkbench(900, 900);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "tablet");
    expect(screen.queryByTestId("editor-splitter")).toBeNull();
    expect(screen.getByTestId("tablet-shell")).toBeInTheDocument();
    expect(screen.getByTestId("canvas")).toBeInTheDocument();
  });

  it("breakpointSelectsPhone", () => {
    renderWorkbench(390, 844);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "phone");
    expect(screen.queryByTestId("editor-splitter")).toBeNull();
  });

  it("breakpointSelectsCover", () => {
    renderWorkbench(349, 316);
    expect(screen.getByTestId("editor-shell")).toHaveAttribute("data-mode", "cover");
    expect(screen.queryByTestId("editor-splitter")).toBeNull();
    expect(screen.getByTestId("cover-shell")).toBeInTheDocument();
    expect(screen.getByTestId("canvas")).toBeInTheDocument();
  });

  it("toolbarHasMoveHandZoomAndAdd", () => {
    renderWorkbench();
    for (const name of ["Move tool", "Hand tool", "Zoom tool", "Add layer", "Effects"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });

  it("toolbarTargetsAreAtLeast44px", () => {
    renderWorkbench();
    for (const name of ["Move tool", "Hand tool", "Zoom tool", "Add layer", "Effects"]) {
      const button = screen.getByRole("button", { name });
      expect(parseFloat(button.style.minHeight)).toBeGreaterThanOrEqual(44);
      expect(parseFloat(button.style.minWidth)).toBeGreaterThanOrEqual(44);
    }
  });

  it("documentMenuHasSaveOpenPrintExport", async () => {
    renderWorkbench();
    const trigger = screen.getByRole("button", { name: "Open document menu" });
    fireEvent.pointerDown(trigger);
    fireEvent.click(trigger);
    for (const item of ["Save .add", "Open .add", "Print / Save PDF", "Export PNG"]) {
      expect(await screen.findByRole("menuitem", { name: item })).toBeInTheDocument();
    }
  });

  it("nativeLensChipIsPresent", () => {
    renderWorkbench();
    const lens = screen.getByRole("button", { name: "Native lens" });
    expect(lens).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(lens);
    expect(lens).toHaveAttribute("aria-pressed", "true");
  });

  it("statusBarShowsZoomAndLayerCount", () => {
    renderWorkbench();
    const status = screen.getByTestId("status-bar");
    expect(status).toHaveTextContent("100%");
    expect(status).toHaveTextContent("3 layers");
  });

  it("defaultRouteRendersTheEditorShell", () => {
    setViewport(1280, 1000);
    window.history.replaceState({}, "", "/");
    render(<App />);
    expect(screen.getByTestId("editor-shell")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "BMO Editor" })).toBeNull();
  });

  it("shaderPresetPublishesPersistentEffectState", async () => {
    renderWorkbench();
    fireEvent.click(screen.getByRole("button", { name: /^Effects$/ }));
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    const ground = screen.getByTestId("editor-artboard").querySelector(".artboard-ground");
    expect(ground).toHaveAttribute("data-effect", "shader");
    fireEvent.click(screen.getByTestId("shader-preset-paper"));
    await waitFor(() => expect(ground).toHaveAttribute("data-effect-preset", "Paper texture"));
  });

  it("texturePresetPublishesPersistentLayerEffectState", async () => {
    renderWorkbench();
    fireEvent.click(screen.getByRole("button", { name: /^Effects$/ }));
    fireEvent.click(screen.getByTestId("catalogue-texture-lab"));
    await waitFor(() => expect(screen.getByTestId("plugin-texture-lab")).toBeInTheDocument());
    // The right panel's Effects tab is inert in jsdom, so the plugin controls are hidden from role queries.
    fireEvent.click(screen.getByRole("button", { name: /^Layer$/, hidden: true }));
    fireEvent.click(screen.getByTestId("texture-preset-paper"));
    const clip = await screen.findByTestId("layer-effect-clip");
    expect(clip).toHaveAttribute("data-effect", "texture");
    expect(clip).toHaveAttribute("data-effect-preset", "PaperTexture");
  });

  it("editorFontsAreLocal", () => {
    const tokens = readFileSync(resolve(process.cwd(), "src/styles/tokens.css"), "utf8");
    const manifest = JSON.parse(readFileSync(resolve(process.cwd(), "asset-manifest.json"), "utf8"));
    expect(tokens).toContain('url("../assets/fonts/archivo-600.woff2")');
    expect(tokens).toContain('url("../assets/fonts/plex-mono-400.woff2")');
    expect(tokens).toContain('url("../assets/fonts/plex-mono-600.woff2")');
    for (const path of [
      "src/assets/fonts/archivo-600.woff2",
      "src/assets/fonts/plex-mono-400.woff2",
      "src/assets/fonts/plex-mono-600.woff2",
    ]) {
      expect(manifest.assets.some((asset: { path: string }) => asset.path === path)).toBe(true);
    }
  });
});
