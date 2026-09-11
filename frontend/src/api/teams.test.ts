import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchTeams } from "./teams";
import { teamFixture } from "./testFixtures";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("fetchTeams", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("requests GET /api/teams and parses the response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([teamFixture()]));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchTeams();

    expect(fetchMock).toHaveBeenCalledWith("/api/teams", expect.objectContaining({ method: "GET" }));
    expect(result[0].abbreviation).toBe("GSW");
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([teamFixture()]));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchTeams({ signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith("/api/teams", expect.objectContaining({ signal: controller.signal }));
  });
});
