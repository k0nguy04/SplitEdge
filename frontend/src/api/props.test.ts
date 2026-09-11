import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchPropCatalog } from "./props";
import { propCatalogFixture } from "./testFixtures";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("fetchPropCatalog", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("requests GET /api/props and parses the response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(propCatalogFixture()));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchPropCatalog();

    expect(fetchMock).toHaveBeenCalledWith("/api/props", expect.objectContaining({ method: "GET" }));
    expect(result).toHaveLength(8);
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(propCatalogFixture()));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchPropCatalog({ signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith("/api/props", expect.objectContaining({ signal: controller.signal }));
  });
});
