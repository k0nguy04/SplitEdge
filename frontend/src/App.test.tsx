import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("App", () => {
  it("explains the product purpose", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { name: /understand the matchup behind the prop/i }),
    ).toBeInTheDocument();
  });
});
