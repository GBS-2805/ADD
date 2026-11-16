import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwinShell } from "./TwinShell";
import { TwinProvider, useTwinStore } from "../twin/store";

function HistoryProbe() {
  const store = useTwinStore();
  return <span data-testid="can-undo">{String(store.canUndo)}</span>;
}

function PngAdder() {
  const store = useTwinStore();
  return (
    <button
      type="button"
      onClick={() => {
        void store.addImageLayer(new File([new Uint8Array([137, 80, 78, 71])], "layer.png", { type: "image/png" }));
      }}
    >
      Add fixture PNG
    </button>
  );
}

function renderTwin() {
  return render(
    <TwinProvider>
      <TwinShell />
      <HistoryProbe />
      <PngAdder />
    </TwinProvider>,
  );
}

function openInspector(layerId = "type-open-studio") {
  fireEvent.click(screen.getByTestId(`art-layer-${layerId}`));
}

describe("Inspector", () => {
  it("shows selected text, shape and image layers", async () => {
    vi.stubGlobal("createImageBitmap", vi.fn(async () => ({ width: 32, height: 24, close: vi.fn() })));
    renderTwin();
    openInspector();
    expect(screen.getByTestId("inspector-kind")).toHaveTextContent("Text layer");
    expect(screen.getByTestId("inspector-layer-name")).toHaveTextContent("Type / OPEN STUDIO");
    expect(screen.getByRole("textbox", { name: "Text content" })).toHaveValue("OPEN\nSTUDIO");

    openInspector("shape-star");
    expect(screen.getByTestId("inspector-kind")).toHaveTextContent("Shape layer");
    expect(screen.getByTestId("inspector-layer-name")).toHaveTextContent("Shape / Star");

    fireEvent.click(screen.getByRole("button", { name: "Add fixture PNG" }));
    await waitFor(() => expect(screen.getByTestId("inspector-kind")).toHaveTextContent("PNG layer"));
    expect(screen.getByText("32 × 24 px")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Fill / })).not.toBeInTheDocument();
  });

  it("lists every layer in the Layers tab", async () => {
    renderTwin();
    openInspector();
    fireEvent.click(screen.getByRole("tab", { name: "Layers" }));

    const list = await screen.findByRole("list", { name: "Layers" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    expect(within(list).getByText("Shape / Orbit")).toBeInTheDocument();
    fireEvent.click(within(list).getByRole("button", { name: "Select Shape / Orbit" }));
    expect(screen.getByTestId("art-layer-shape-orbit")).toHaveClass("is-selected");
  });

  it("updates transform values from number inputs", async () => {
    renderTwin();
    openInspector();
    const increase = screen.getByRole("button", { name: "Increase X" });
    const input = screen.getByRole("spinbutton", { name: "X" });
    fireEvent.pointerDown(increase, { pointerType: "mouse", button: 0 });
    await waitFor(() => expect(input).toHaveAttribute("aria-valuenow", "1"));
    fireEvent.pointerUp(increase, { pointerType: "mouse", button: 0 });

    await waitFor(() => expect(screen.getByTestId("art-layer-type-open-studio").getAttribute("style")).toContain(
      "translate(calc(-50% + 1px), calc(-50% + -8px))",
    ));
  });

  it("nudges the movement dials by five", async () => {
    renderTwin();
    openInspector();

    fireEvent.click(screen.getByRole("button", { name: "Increase X by 5" }));
    fireEvent.click(screen.getByRole("button", { name: "Decrease Y by 5" }));

    await waitFor(() => expect(screen.getByTestId("art-layer-type-open-studio").getAttribute("style")).toContain(
      "translate(calc(-50% + 5px), calc(-50% + -13px))",
    ));
  });

  it("changes the font on a text layer", async () => {
    renderTwin();
    openInspector();

    fireEvent.click(screen.getByRole("combobox", { name: "Font" }));
    fireEvent.click(await screen.findByRole("option", { name: /Spectral/ }));

    await waitFor(() => expect(screen.getByLabelText("Open Studio headline")).toHaveStyle({
      fontFamily: '"Spectral", Georgia, serif',
    }));
  });

  it("updates opacity from the slider", async () => {
    renderTwin();
    openInspector();
    const thumb = screen.getByRole("slider", { hidden: true });
    expect(thumb).toHaveAttribute("aria-label", "Opacity");
    fireEvent.focus(thumb);
    fireEvent.keyDown(thumb, { key: "Home" });
    await waitFor(() => expect(thumb).toHaveAttribute("aria-valuenow", "0"));
    expect(screen.getByTestId("art-layer-type-open-studio")).toHaveStyle({ opacity: "0" });
  });

  it("toggles visibility with the switch", async () => {
    renderTwin();
    openInspector();
    fireEvent.click(screen.getByRole("checkbox", { name: "Visible" }));

    await waitFor(() => expect(screen.getByTestId("art-layer-type-open-studio")).toHaveStyle({ display: "none" }));
  });

  it("updates text and shape fills from the neutral ColorPicker", async () => {
    renderTwin();
    openInspector();
    fireEvent.click(screen.getByRole("button", { name: "Fill" }));
    fireEvent.click(await screen.findByRole("button", { name: "Use fill #5a5a56" }));

    await waitFor(() => expect(screen.getByLabelText("Open Studio headline")).toHaveStyle({ color: "rgb(90, 90, 86)" }));
  });

  it("renames a layer with Editable", async () => {
    renderTwin();
    openInspector();
    fireEvent.click(screen.getByRole("button", { name: "Edit layer name" }));
    const input = screen.getByDisplayValue("Type / OPEN STUDIO");
    fireEvent.change(input, { target: { value: "Type / Renamed" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => expect(screen.getByTestId("inspector-layer-name")).toHaveTextContent("Type / Renamed"));
  });

  it("records direct edits in document history", async () => {
    renderTwin();
    openInspector();
    expect(screen.getByTestId("can-undo")).toHaveTextContent("false");

    fireEvent.click(screen.getByRole("checkbox", { name: "Visible" }));

    await waitFor(() => expect(screen.getByTestId("can-undo")).toHaveTextContent("true"));
  });

  it("replaces the inspector when plugins open in the same slot", async () => {
    renderTwin();
    openInspector();
    fireEvent.click(screen.getByRole("button", { name: "Open the plugin catalogue" }));

    await waitFor(() => expect(screen.queryByTestId("inspector")).not.toBeInTheDocument());
    expect(screen.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "plugins");
    expect(screen.getByTestId("catalogue-search")).toBeInTheDocument();
  });
});
