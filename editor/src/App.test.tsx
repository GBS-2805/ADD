import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import App from "./App";

function renderApp() {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: 1440 });
  Object.defineProperty(window, "innerHeight", { configurable: true, value: 900 });
  window.history.replaceState({}, "", "/");
  return render(<App />);
}

describe("BMO Editor", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("renders the editor shell by default", () => {
    renderApp();

    expect(screen.getByTestId("editor-shell")).toBeInTheDocument();
    expect(screen.getByTestId("editor-splitter")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "BMO Editor" })).toBeNull();
  });

  it("opens the command palette with the keyboard", async () => {
    renderApp();

    fireEvent.keyDown(screen.getByRole("region", { name: "Artboard canvas" }), {
      key: "k",
      code: "KeyK",
      metaKey: true,
    });

    expect(await screen.findByTestId("command-palette")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Search actions" })).toHaveFocus();
  });
});
