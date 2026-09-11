/**
 * Fixture JSON shapes copied from the backend's actual contracts, as pinned by
 * `PlayerControllerTest`, `TeamControllerTest`, `PropCatalogControllerTest`,
 * `DataStatusControllerTest`, and `MatchupReportControllerTest`. Used only by
 * this package's own tests - never used to make a live request.
 */

export function activePlayerFixture(): unknown {
  return {
    nbaPlayerId: 201939,
    firstName: "Stephen",
    lastName: "Curry",
    fullName: "Stephen Curry",
    nbaTeamId: 1610612744,
    active: true,
  };
}

/** No current roster team: the backend omits `nbaTeamId` entirely (not `null`). */
export function freeAgentPlayerFixture(): unknown {
  return {
    nbaPlayerId: 500,
    firstName: "Free",
    lastName: "Agent",
    fullName: "Free Agent",
    active: true,
  };
}

export function teamFixture(): unknown {
  return {
    nbaTeamId: 1610612744,
    abbreviation: "GSW",
    city: "Golden State",
    nickname: "Warriors",
    fullName: "Golden State Warriors",
  };
}

export function propCatalogFixture(): unknown {
  return [
    { code: "POINTS", displayName: "Points", componentStats: ["POINTS"] },
    { code: "REBOUNDS", displayName: "Rebounds", componentStats: ["REBOUNDS"] },
    { code: "ASSISTS", displayName: "Assists", componentStats: ["ASSISTS"] },
    { code: "THREE_POINTERS_MADE", displayName: "Three-Pointers Made", componentStats: ["THREE_POINTERS_MADE"] },
    { code: "PR", displayName: "Points + Rebounds", componentStats: ["POINTS", "REBOUNDS"] },
    { code: "PA", displayName: "Points + Assists", componentStats: ["POINTS", "ASSISTS"] },
    { code: "RA", displayName: "Rebounds + Assists", componentStats: ["REBOUNDS", "ASSISTS"] },
    { code: "PRA", displayName: "Points + Rebounds + Assists", componentStats: ["POINTS", "REBOUNDS", "ASSISTS"] },
  ];
}

export function readyDataStatusFixture(): unknown {
  return {
    dataImported: true,
    status: "READY",
    latestGamesStatsImport: {
      runId: 4,
      completedAt: "2025-03-02T08:15:30Z",
      recordsProcessed: 82747,
      recordsFailed: 0,
    },
    seasons: ["2023-24", "2024-25", "2025-26"],
    counts: { games: 3690, playerGameStats: 79057, players: 801, activePlayers: 525, teams: 30 },
  };
}

/** No completed import yet: the backend omits `latestGamesStatsImport` entirely. */
export function emptyDataStatusFixture(): unknown {
  return {
    dataImported: false,
    status: "EMPTY",
    seasons: [],
    counts: { games: 0, playerGameStats: 0, players: 0, activePlayers: 0, teams: 0 },
  };
}

export function matchupReportFixture(): unknown {
  return {
    criteria: {
      player: { nbaPlayerId: 201939, fullName: "Test Player" },
      opponent: { nbaTeamId: 1610612738, abbreviation: "BOS", fullName: "Boston Celtics" },
      prop: "POINTS",
      line: 24.5,
      direction: "OVER",
      seasonsApplied: ["2024-25"],
      location: "ALL",
      recency: "ALL",
      minMinutes: 0,
    },
    matchup: { qualifyingGames: 3, hits: 1, misses: 2, pushes: 0, hitRate: 0.333, average: 23.3, median: 20.0 },
    baseline: { qualifyingGames: 4, hits: 2, misses: 2, pushes: 0, hitRate: 0.5, average: 24.5, median: 24.0 },
    comparison: { hitRateDifferencePoints: -16.7 },
    sampleQuality: "LOW",
    dataFreshness: { importType: "GAMES_STATS", completedAt: "2025-03-02T08:15:30Z" },
    opponentContext: { available: false, reason: "TEAM_DEFENSE_NOT_IMPORTED" },
    games: [
      {
        nbaGameId: "0022400001",
        gameDate: "2024-10-22",
        season: "2024-25",
        location: "HOME",
        nbaTeamId: 1610612744,
        opponentNbaTeamId: 1610612738,
        minutes: 34.5,
        propValue: 30,
        line: 24.5,
        result: "HIT",
      },
      {
        nbaGameId: "0022400002",
        gameDate: "2024-11-05",
        season: "2024-25",
        location: "AWAY",
        nbaTeamId: 1610612744,
        opponentNbaTeamId: 1610612738,
        minutes: 30.1,
        propValue: 20,
        line: 24.5,
        result: "MISS",
      },
      {
        nbaGameId: "0022400003",
        gameDate: "2024-12-19",
        season: "2024-25",
        location: "HOME",
        nbaTeamId: 1610612744,
        opponentNbaTeamId: 1610612738,
        minutes: 33.2,
        propValue: 20,
        line: 24.5,
        result: "MISS",
      },
    ],
    chart: [
      { nbaGameId: "0022400001", gameDate: "2024-10-22", propValue: 30, result: "HIT" },
      { nbaGameId: "0022400002", gameDate: "2024-11-05", propValue: 20, result: "MISS" },
      { nbaGameId: "0022400003", gameDate: "2024-12-19", propValue: 20, result: "MISS" },
    ],
    warnings: [],
  };
}

/** Zero qualifying games: hitRate/average/median are all null, sampleQuality is omitted. */
export function zeroQualifyingGamesReportFixture(): unknown {
  const base = matchupReportFixture() as Record<string, unknown>;
  delete base.sampleQuality;
  return {
    ...base,
    matchup: { qualifyingGames: 0, hits: 0, misses: 0, pushes: 0, hitRate: null, average: null, median: null },
    comparison: { hitRateDifferencePoints: null },
    games: [],
    chart: [],
    warnings: ["NO_QUALIFYING_GAMES"],
  };
}

/** No completed GAMES_STATS import: dataFreshness.completedAt is null. */
export function missingFreshnessReportFixture(): unknown {
  const base = matchupReportFixture() as Record<string, unknown>;
  return {
    ...base,
    dataFreshness: { importType: "GAMES_STATS", completedAt: null },
    warnings: ["NO_SUCCESSFUL_GAMES_STATS_IMPORT"],
  };
}

/** Malformed: a deeply nested field has the wrong type. */
export function malformedMatchupReportFixture(): unknown {
  const base = matchupReportFixture() as Record<string, unknown>;
  return {
    ...base,
    matchup: { ...(base.matchup as Record<string, unknown>), qualifyingGames: "three" },
  };
}

/** Invalid: an enum-typed field holds a value the backend never produces. */
export function invalidEnumMatchupReportFixture(): unknown {
  const base = matchupReportFixture() as Record<string, unknown>;
  const games = base.games as Record<string, unknown>[];
  return {
    ...base,
    games: [{ ...games[0], result: "SIDEWAYS" }, ...games.slice(1)],
  };
}

export function apiErrorResponseFixture(): unknown {
  return {
    code: "UNKNOWN_PLAYER",
    message: "no stored player has that NBA player ID",
    fieldErrors: [{ field: "nbaPlayerId", code: "UNKNOWN_PLAYER", message: "no stored player has that NBA player ID" }],
    path: "/api/players/123456789",
    timestamp: "2025-03-02T08:15:30Z",
  };
}
