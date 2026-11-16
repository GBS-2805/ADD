import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "../App";

function openAddMenu() {
  fireEvent.click(screen.getByRole("button", { name: "Add a layer" }));
}

describe("Add menu", () => {
  it("lists text, shape and png", async () => {
    render(<App />);
    openAddMenu();

    expect(await screen.findByRole("menuitem", { name: /^Text/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /^Shape/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /^PNG/ })).toBeInTheDocument();
  });

  it("creates a text layer and opens the inspector", async () => {
    render(<App />);
    openAddMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: /^Text/ }));

    await waitFor(() => expect(screen.getByTestId("inspector")).toBeInTheDocument());
    expect(screen.getByLabelText("Text / Type something 2 text")).toHaveTextContent("Type something");
    expect(screen.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "inspector");
  });

  it("creates a shape layer from the nested shape menu", async () => {
    render(<App />);
    openAddMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: /^Shape/ }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /^Star/ }));

    await waitFor(() => expect(screen.getByTestId("inspector")).toBeInTheDocument());
    expect(screen.getAllByLabelText("Select Shape / Star 2").length).toBeGreaterThan(0);
    expect(screen.getByTestId("inspector-layer-name")).toHaveTextContent("Shape / Star 2");
  });

  it("routes png to Overlay Lab", async () => {
    render(<App />);
    openAddMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: /^PNG/ }));

    await waitFor(() => expect(screen.getByTestId("plugin-overlay-lab")).toBeInTheDocument());
    expect(screen.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "plugins");
  });
});
