import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("BMO Twin", () => {
  it("renders the app root and the BMO Twin heading", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "BMO Twin" })).toBeInTheDocument();
    expect(document.getElementById("bmo-twin")).toBeInTheDocument();
  });

  it("opens the plugin catalogue from the dock", () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Open the plugin catalogue" }));

    expect(screen.getByRole("heading", { name: "Eight ways to make a mark" })).toBeInTheDocument();
    expect(screen.getByTestId("catalogue-search")).toBeInTheDocument();
  });
});
