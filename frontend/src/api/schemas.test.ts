import { describe, expect, it } from "vitest";

import {
  ApiErrorResponseSchema,
  DataStatusResponseSchema,
  LineSchema,
  MatchupReportRequestSchema,
  MatchupReportResponseSchema,
  MinMinutesSchema,
  PlayerListResponseSchema,
  PlayerResponseSchema,
  PropCatalogResponseSchema,
  TeamListResponseSchema,
} from "./schemas";
import {
  activePlayerFixture,
  apiErrorResponseFixture,
  emptyDataStatusFixture,
  freeAgentPlayerFixture,
  invalidEnumMatchupReportFixture,
  malformedMatchupReportFixture,
  matchupReportFixture,
  missingFreshnessReportFixture,
  propCatalogFixture,
  readyDataStatusFixture,
  teamFixture,
  zeroQualifyingGamesReportFixture,
} from "./testFixtures";

describe("PlayerResponseSchema", () => {
  it("parses an active player with a roster team", () => {
    const result = PlayerResponseSchema.parse(activePlayerFixture());
    expect(result.nbaPlayerId).toBe(201939);
    expect(result.nbaTeamId).toBe(1610612744);
    expect(result.active).toBe(true);
  });

  it("normalizes an omitted nbaTeamId to null rather than undefined", () => {
    const result = PlayerResponseSchema.parse(freeAgentPlayerFixture());
    expect(result.nbaTeamId).toBeNull();
  });

  it("parses a list of players", () => {
    const result = PlayerListResponseSchema.parse([activePlayerFixture(), freeAgentPlayerFixture()]);
    expect(result).toHaveLength(2);
  });

  it("rejects a player missing a required field", () => {
    const broken = { ...(activePlayerFixture() as Record<string, unknown>) };
    delete broken.fullName;
    expect(PlayerResponseSchema.safeParse(broken).success).toBe(false);
  });
});

describe("TeamListResponseSchema", () => {
  it("parses a list of teams", () => {
    const result = TeamListResponseSchema.parse([teamFixture()]);
    expect(result[0].abbreviation).toBe("GSW");
  });

  it("rejects an empty array element with the wrong type", () => {
    expect(TeamListResponseSchema.safeParse([{ ...(teamFixture() as object), nbaTeamId: "not-a-number" }]).success).toBe(
      false,
    );
  });
});

describe("PropCatalogResponseSchema", () => {
  it("parses the full 8-entry catalog", () => {
    const result = PropCatalogResponseSchema.parse(propCatalogFixture());
    expect(result).toHaveLength(8);
    expect(result[4].componentStats).toEqual(["POINTS", "REBOUNDS"]);
  });

  it("rejects an entry with an empty componentStats array", () => {
    const broken = [{ code: "POINTS", displayName: "Points", componentStats: [] }];
    expect(PropCatalogResponseSchema.safeParse(broken).success).toBe(false);
  });
});

describe("DataStatusResponseSchema", () => {
  it("parses a ready status with a completed import", () => {
    const result = DataStatusResponseSchema.parse(readyDataStatusFixture());
    expect(result.status).toBe("READY");
    expect(result.latestGamesStatsImport?.runId).toBe(4);
    expect(result.counts.activePlayers).toBe(525);
  });

  it("normalizes an omitted latestGamesStatsImport to null", () => {
    const result = DataStatusResponseSchema.parse(emptyDataStatusFixture());
    expect(result.latestGamesStatsImport).toBeNull();
  });

  it("rejects an unknown status value", () => {
    const broken = { ...(emptyDataStatusFixture() as Record<string, unknown>), status: "UNKNOWN" };
    expect(DataStatusResponseSchema.safeParse(broken).success).toBe(false);
  });
});

describe("MatchupReportResponseSchema", () => {
  it("parses a full, successful report", () => {
    const result = MatchupReportResponseSchema.parse(matchupReportFixture());
    expect(result.matchup.hitRate).toBeCloseTo(0.333);
    expect(result.sampleQuality).toBe("LOW");
    expect(result.games).toHaveLength(3);
    expect(result.chart).toHaveLength(3);
  });

  it("parses a zero-qualifying-game report with null rate/average/median and no sample quality", () => {
    const result = MatchupReportResponseSchema.parse(zeroQualifyingGamesReportFixture());
    expect(result.matchup.qualifyingGames).toBe(0);
    expect(result.matchup.hitRate).toBeNull();
    expect(result.matchup.average).toBeNull();
    expect(result.matchup.median).toBeNull();
    expect(result.comparison.hitRateDifferencePoints).toBeNull();
    expect(result.sampleQuality).toBeNull();
  });

  it("parses a report with a missing data-freshness completedAt", () => {
    const result = MatchupReportResponseSchema.parse(missingFreshnessReportFixture());
    expect(result.dataFreshness.completedAt).toBeNull();
    expect(result.dataFreshness.importType).toBe("GAMES_STATS");
  });

  it("rejects a malformed nested field (matchup.qualifyingGames as a string)", () => {
    expect(MatchupReportResponseSchema.safeParse(malformedMatchupReportFixture()).success).toBe(false);
  });

  it("rejects an invalid enum value in a supporting game's result", () => {
    expect(MatchupReportResponseSchema.safeParse(invalidEnumMatchupReportFixture()).success).toBe(false);
  });

  it("rejects a report missing an entire required nested object", () => {
    const broken = { ...(matchupReportFixture() as Record<string, unknown>) };
    delete broken.opponentContext;
    expect(MatchupReportResponseSchema.safeParse(broken).success).toBe(false);
  });
});

describe("ApiErrorResponseSchema", () => {
  it("parses a real error body shape", () => {
    const result = ApiErrorResponseSchema.parse(apiErrorResponseFixture());
    expect(result.code).toBe("UNKNOWN_PLAYER");
    expect(result.fieldErrors[0].field).toBe("nbaPlayerId");
  });
});

/**
 * Shared numeric-value-precision behavior for `line` (0-999) and
 * `minMinutes` (0-80): both validate the numeric *value* (range +
 * thousandths precision), never the original JSON token's lexical scale -
 * see the module-level comment in `schemas.ts` for why that distinction is
 * both unrecoverable and irrelevant here.
 */
describe.each([
  { name: "LineSchema", schema: LineSchema, max: 999 },
  { name: "MinMinutesSchema", schema: MinMinutesSchema, max: 80 },
])("$name", ({ schema, max }) => {
  it("accepts the lower bound (0)", () => {
    expect(schema.safeParse(0).success).toBe(true);
  });

  it("accepts the upper bound", () => {
    expect(schema.safeParse(max).success).toBe(true);
  });

  it("accepts a plain integer", () => {
    expect(schema.safeParse(24).success).toBe(true);
  });

  it("accepts one, two, and three decimal places", () => {
    expect(schema.safeParse(24.1).success).toBe(true);
    expect(schema.safeParse(24.12).success).toBe(true);
    expect(schema.safeParse(24.125).success).toBe(true);
  });

  it("accepts 24.5 parsed from JSON text \"24.5000\" - the trailing zeros are already gone", () => {
    const parsedFromVerboseText = JSON.parse("24.5000") as number;
    expect(parsedFromVerboseText).toBe(24.5);
    expect(schema.safeParse(parsedFromVerboseText).success).toBe(true);
  });

  it("rejects a negative value", () => {
    expect(schema.safeParse(-0.001).success).toBe(false);
  });

  it("rejects a value above the upper bound", () => {
    expect(schema.safeParse(max + 0.001).success).toBe(false);
  });

  it("rejects meaningful precision beyond three decimals", () => {
    expect(schema.safeParse(24.1234).success).toBe(false);
  });

  it("rejects a numeric string", () => {
    expect(schema.safeParse("24.5").success).toBe(false);
  });

  it("rejects NaN and positive/negative infinity", () => {
    expect(schema.safeParse(Number.NaN).success).toBe(false);
    expect(schema.safeParse(Number.POSITIVE_INFINITY).success).toBe(false);
    expect(schema.safeParse(Number.NEGATIVE_INFINITY).success).toBe(false);
  });
});

describe("MatchupReportRequestSchema numeric rules", () => {
  const base = {
    nbaPlayerId: 201939,
    nbaOpponentTeamId: 1610612738,
    prop: "POINTS" as const,
    line: 24.5,
    direction: "OVER" as const,
  };

  it("allows minMinutes to be omitted", () => {
    const result = MatchupReportRequestSchema.safeParse(base);
    expect(result.success).toBe(true);
  });

  it("allows minMinutes to be explicitly null", () => {
    const result = MatchupReportRequestSchema.safeParse({ ...base, minMinutes: null });
    expect(result.success).toBe(true);
  });

  it("accepts a valid minMinutes value", () => {
    const result = MatchupReportRequestSchema.safeParse({ ...base, minMinutes: 20.125 });
    expect(result.success).toBe(true);
  });

  it("rejects minMinutes when present but out of range", () => {
    expect(MatchupReportRequestSchema.safeParse({ ...base, minMinutes: 80.5 }).success).toBe(false);
  });

  it("rejects minMinutes when present but over-precise", () => {
    expect(MatchupReportRequestSchema.safeParse({ ...base, minMinutes: 20.1234 }).success).toBe(false);
  });

  it("rejects an out-of-range line", () => {
    expect(MatchupReportRequestSchema.safeParse({ ...base, line: 1000 }).success).toBe(false);
  });

  it("rejects a negative line", () => {
    expect(MatchupReportRequestSchema.safeParse({ ...base, line: -1 }).success).toBe(false);
  });
});

describe("canonical NBA ID strictness in response schemas", () => {
  it("rejects a fractional nbaPlayerId in PlayerResponse", () => {
    const broken = { ...(activePlayerFixture() as Record<string, unknown>), nbaPlayerId: 201939.5 };
    expect(PlayerResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a zero nbaPlayerId in PlayerResponse", () => {
    const broken = { ...(activePlayerFixture() as Record<string, unknown>), nbaPlayerId: 0 };
    expect(PlayerResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a negative nbaTeamId in PlayerResponse when present", () => {
    const broken = { ...(activePlayerFixture() as Record<string, unknown>), nbaTeamId: -1610612744 };
    expect(PlayerResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a fractional nbaTeamId in TeamResponse", () => {
    const broken = { ...(teamFixture() as Record<string, unknown>), nbaTeamId: 1610612744.25 };
    expect(TeamListResponseSchema.safeParse([broken]).success).toBe(false);
  });

  it("rejects a zero nbaTeamId in TeamResponse", () => {
    const broken = { ...(teamFixture() as Record<string, unknown>), nbaTeamId: 0 };
    expect(TeamListResponseSchema.safeParse([broken]).success).toBe(false);
  });

  it("rejects a negative criteria.player.nbaPlayerId in the matchup report response", () => {
    const base = matchupReportFixture() as Record<string, unknown>;
    const criteria = base.criteria as Record<string, unknown>;
    const player = criteria.player as Record<string, unknown>;
    const broken = { ...base, criteria: { ...criteria, player: { ...player, nbaPlayerId: -201939 } } };
    expect(MatchupReportResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a fractional criteria.opponent.nbaTeamId in the matchup report response", () => {
    const base = matchupReportFixture() as Record<string, unknown>;
    const criteria = base.criteria as Record<string, unknown>;
    const opponent = criteria.opponent as Record<string, unknown>;
    const broken = { ...base, criteria: { ...criteria, opponent: { ...opponent, nbaTeamId: 1610612738.5 } } };
    expect(MatchupReportResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a zero nbaTeamId in a supporting game", () => {
    const base = matchupReportFixture() as Record<string, unknown>;
    const games = base.games as Record<string, unknown>[];
    const broken = { ...base, games: [{ ...games[0], nbaTeamId: 0 }, ...games.slice(1)] };
    expect(MatchupReportResponseSchema.safeParse(broken).success).toBe(false);
  });

  it("rejects a negative opponentNbaTeamId in a supporting game", () => {
    const base = matchupReportFixture() as Record<string, unknown>;
    const games = base.games as Record<string, unknown>[];
    const broken = { ...base, games: [{ ...games[0], opponentNbaTeamId: -1610612738 }, ...games.slice(1)] };
    expect(MatchupReportResponseSchema.safeParse(broken).success).toBe(false);
  });
});
