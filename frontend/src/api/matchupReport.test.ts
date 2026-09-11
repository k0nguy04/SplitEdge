import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchMatchupReport } from "./matchupReport";
import type { MatchupReportRequest } from "./schemas";
import { matchupReportFixture } from "./testFixtures";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

const validRequest: MatchupReportRequest = {
  nbaPlayerId: 201939,
  nbaOpponentTeamId: 1610612738,
  prop: "POINTS",
  line: 24.5,
  direction: "OVER",
  season: "2024-25",
  location: "ALL",
  recency: "ALL",
  minMinutes: 0,
};

describe("fetchMatchupReport", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends POST /api/reports/matchup with the exact JSON body and content type", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(matchupReportFixture()));
    vi.stubGlobal("fetch", fetchMock);

    await fetchMatchupReport(validRequest);

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/reports/matchup",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(validRequest),
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      }),
    );
  });

  it("omits optional fields the caller left undefined rather than sending literal nulls", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(matchupReportFixture()));
    vi.stubGlobal("fetch", fetchMock);

    const minimalRequest: MatchupReportRequest = {
      nbaPlayerId: 201939,
      nbaOpponentTeamId: 1610612738,
      prop: "POINTS",
      line: 24.5,
      direction: "OVER",
    };

    await fetchMatchupReport(minimalRequest);

    const [, init] = fetchMock.mock.calls[0] as [string, { body: string }];
    const sentBody = JSON.parse(init.body) as Record<string, unknown>;
    expect(sentBody).not.toHaveProperty("season");
    expect(sentBody).not.toHaveProperty("location");
    expect(sentBody).not.toHaveProperty("recency");
    expect(sentBody).not.toHaveProperty("minMinutes");
    expect(sentBody.nbaPlayerId).toBe(201939);
  });

  it("parses a successful response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(matchupReportFixture())));

    const result = await fetchMatchupReport(validRequest);

    expect(result.matchup.qualifyingGames).toBe(3);
    expect(result.sampleQuality).toBe("LOW");
  });

  it("rejects (as a promise) rather than throws synchronously for an invalid outgoing request", async () => {
    vi.stubGlobal("fetch", vi.fn());

    const invalidRequest = { ...validRequest, direction: "SIDEWAYS" } as unknown as MatchupReportRequest;

    await expect(fetchMatchupReport(invalidRequest)).rejects.toBeTruthy();
  });

  it("passes an AbortSignal through", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(matchupReportFixture()));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await fetchMatchupReport(validRequest, { signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/reports/matchup",
      expect.objectContaining({ signal: controller.signal }),
    );
  });
});
