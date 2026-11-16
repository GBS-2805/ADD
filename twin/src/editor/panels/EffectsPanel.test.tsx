import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";
import { TwinProvider, useTwinStore, type TwinActions } from "../../twin/store";
import { EffectsPanel } from "./EffectsPanel";

let current: TwinActions | null = null;

function Bridge() {
  const store = useTwinStore();
  useEffect(() => {
    current = store;
  });
  return (
    <>
      <button type="button" onClick={() => store.selectLayers(["type-open-studio", "shape-star"], "replace")}>Select two</button>
      <button type="button" onClick={store.toggleNativeLens}>Toggle lens</button>
      <output data-testid="background">{JSON.stringify(store.state.doc.background)}</output>
      <output data-testid="layer-effect">{JSON.stringify(store.state.doc.layers.find((layer) => layer.id === "type-open-studio")?.effect)}</output>
      <output data-testid="toast">{store.state.toast}</output>
      <EffectsPanel />
    </>
  );
}

const renderEffects = () => render(
  <TwinProvider>
    <Bridge />
  </TwinProvider>,
);

describe("EffectsPanel", () => {
  it("effectsTabShowsCatalogue", () => {
    renderEffects();
    expect(screen.getByTestId("effects-panel")).toBeInTheDocument();
    expect(screen.getByTestId("plugin-catalogue")).toBeInTheDocument();
    expect(screen.getByTestId("catalogue-shader-lab")).toBeInTheDocument();
  });

  it("searchFiltersPlugins", () => {
    renderEffects();
    fireEvent.change(screen.getByTestId("catalogue-search"), { target: { value: "shader" } });
    expect(screen.getByTestId("catalogue-shader-lab")).toBeInTheDocument();
    expect(screen.queryByTestId("catalogue-texture-lab")).toBeNull();
  });

  it("opensShaderLab", () => {
    renderEffects();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    expect(screen.getByTestId("plugin-shader-lab")).toBeInTheDocument();
    expect(screen.queryByTestId("plugin-catalogue")).toBeNull();
    expect(current!.state.rightTab).toBe("effects");
  });

  it("appliesShaderToArtboard", async () => {
    renderEffects();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    fireEvent.click(screen.getByTestId("shader-preset-paper"));
    fireEvent.click(screen.getByRole("button", { name: "Apply to artboard" }));
    await waitFor(() => expect(screen.getByTestId("background").textContent).toContain('"kind":"shader"'));
    expect(screen.getByTestId("background").textContent).toContain("Paper texture");
  });

  it("appliesTextureToSingleLayer", async () => {
    renderEffects();
    fireEvent.click(screen.getByTestId("catalogue-texture-lab"));
    fireEvent.click(screen.getByRole("button", { name: "Layer" }));
    fireEvent.click(screen.getByTestId("texture-preset-paper"));
    fireEvent.click(screen.getByRole("button", { name: "Apply to layer" }));
    await waitFor(() => expect(screen.getByTestId("layer-effect").textContent).toContain('"kind":"texture"'));
  });

  it("layerEffectDisabledForMultiSelection", () => {
    renderEffects();
    fireEvent.click(screen.getByRole("button", { name: "Select two" }));
    fireEvent.click(screen.getByTestId("catalogue-texture-lab"));
    expect(screen.getByRole("button", { name: "Layer" })).toBeDisabled();
    expect(screen.getByText("Select one layer for a layer effect.")).toBeInTheDocument();
  });

  it("hidePreservesPluginControls", () => {
    renderEffects();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    const speed = screen.getByRole("slider", { name: /Speed/ });
    fireEvent.change(speed, { target: { value: "0.8" } });
    fireEvent.click(screen.getByRole("button", { name: "Hide" }));
    expect(screen.getByTestId("catalogue-shader-lab")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    expect(screen.getByRole("slider", { name: /Speed/ })).toHaveValue("0.8");
  });

  it("closeResetsPluginControls", () => {
    renderEffects();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    fireEvent.change(screen.getByRole("slider", { name: /Speed/ }), { target: { value: "0.8" } });
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.queryByTestId("plugin-shader-lab")).toBeNull();
    fireEvent.click(screen.getByTestId("catalogue-shader-lab"));
    expect(screen.getByRole("slider", { name: /Speed/ })).toHaveValue("0.5");
  });

  it("nativeLensStillDimsOnly", () => {
    renderEffects();
    const before = screen.getByTestId("background").textContent;
    fireEvent.click(screen.getByRole("button", { name: "Toggle lens" }));
    expect(screen.getByTestId("catalogue-shader-lab")).toHaveClass("is-lens-dimmed");
    expect(screen.getByTestId("background").textContent).toBe(before);
  });

  it("pluginPanelDoesNotDuplicateInspector", () => {
    renderEffects();
    expect(screen.queryByTestId("inspector-panel")).toBeNull();
  });
});
