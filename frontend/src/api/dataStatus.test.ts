import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchDataStatus } from "./dataStatus";
import { readyDataStatusFixture } from "./testFixtures";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("fetchDataStatus", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("requests GET /api/data/status and parses the response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(readyDataStatusFixture()));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchDataStatus();

    expect(fetchMock).toHaveBeenCalledWith("/api/data/status", expect.objectContaining({ method: "GET" }));
    expect(result.status).toBe("READY");
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(readyDataStatusFixture()));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchDataStatus({ signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith("/api/data/status", expect.objectContaining({ signal: controller.signal }));
  });
});
