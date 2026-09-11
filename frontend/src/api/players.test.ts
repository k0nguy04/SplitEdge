import { afterEach, describe, expect, it, vi } from "vitest";

import { InvalidArgumentError } from "./errors";
import { fetchActivePlayers, fetchPlayerById } from "./players";
import { activePlayerFixture } from "./testFixtures";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("fetchActivePlayers", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("defaults an omitted limit to 600, encoded numerically", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([activePlayerFixture()]));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchActivePlayers();

    expect(fetchMock).toHaveBeenCalledWith("/api/players?limit=600", expect.objectContaining({ method: "GET" }));
    expect(result).toHaveLength(1);
  });

  it("encodes an explicit limit numerically in the query string", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    await fetchActivePlayers(50);

    expect(fetchMock).toHaveBeenCalledWith("/api/players?limit=50", expect.objectContaining({ method: "GET" }));
  });

  it.each([1, 1000])("accepts the boundary limit %d", async (limit) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    await fetchActivePlayers(limit);

    expect(fetchMock).toHaveBeenCalledWith(`/api/players?limit=${limit}`, expect.objectContaining({ method: "GET" }));
  });

  it.each([0, -1, 1001, 12.5, NaN, Infinity, -Infinity])(
    "rejects an invalid limit (%p) with InvalidArgumentError and never calls fetch",
    async (limit) => {
      const fetchMock = vi.fn();
      vi.stubGlobal("fetch", fetchMock);

      const error = await fetchActivePlayers(limit).catch((e: unknown) => e);

      expect(error).toBeInstanceOf(InvalidArgumentError);
      expect((error as InvalidArgumentError).kind).toBe("invalidArgument");
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("rejects as a promise rather than throwing synchronously for an invalid limit", async () => {
    vi.stubGlobal("fetch", vi.fn());

    await expect(fetchActivePlayers(-5)).rejects.toBeInstanceOf(InvalidArgumentError);
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchActivePlayers(undefined, { signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/players?limit=600",
      expect.objectContaining({ signal: controller.signal }),
    );
  });
});

describe("fetchPlayerById", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("requests GET /api/players/{id} and parses the response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(activePlayerFixture()));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchPlayerById(201939);

    expect(fetchMock).toHaveBeenCalledWith("/api/players/201939", expect.objectContaining({ method: "GET" }));
    expect(result.nbaPlayerId).toBe(201939);
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(activePlayerFixture()));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchPlayerById(201939, { signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith("/api/players/201939", expect.objectContaining({ signal: controller.signal }));
  });
});
