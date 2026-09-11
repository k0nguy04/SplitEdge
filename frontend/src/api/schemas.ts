import { z } from "zod";

/**
 * Zod schemas mirroring the backend's actual JSON contracts (see the Java
 * records in `backend/src/main/java/dev/splitedge/**\/api/*Response.java` and
 * `dev/splitedge/importstatus/DataStatusResponse.java`). These schemas are the
 * single authoritative frontend boundary: every response is parsed through
 * one of these before the rest of the app ever sees it, and every exported
 * type is inferred from its schema rather than hand-maintained separately.
 *
 * Fields the backend omits entirely when null (Jackson NON_NULL inclusion,
 * confirmed by `PlayerControllerTest`'s `jsonPath(...).doesNotExist()`
 * assertions) use `.nullish()` and are normalized to `null` so the rest of
 * the app only ever deals with `T | null`, never `T | null | undefined`.
 *
 * Unknown-key policy: every `z.object(...)` below uses Zod's default
 * behavior, which silently strips any response key it doesn't declare -
 * nothing here calls `.strict()` or `z.strictObject(...)`, and that is
 * intentional and uniform across every nested object, not an oversight. A
 * *missing* or *renamed* required key still fails to parse (required keys
 * are still enforced), so a real contract break is still caught; only a
 * brand-new *additive* backend field is silently ignored until a schema is
 * updated to read it. This lets the backend add fields (e.g. a future
 * Milestone 4 opponent-context stat) without forcing a lockstep frontend
 * deploy. Do not mix in a `.strict()`/`z.strictObject(...)` schema here
 * without deciding this trade-off is being deliberately reversed for that
 * one shape.
 */

const nullishNumber = z
  .number()
  .nullish()
  .transform((value) => value ?? null);

/**
 * A canonical NBA ID (player or team): a positive integer. Applied to every
 * response field that carries one, so a malformed backend response (a
 * fractional, zero, or negative "ID") is rejected as `MalformedResponseError`
 * rather than silently passed through. `z.number().int().positive()` is
 * sufficient here - real NBA IDs are small enough that no extra
 * safe-integer-specific handling is warranted.
 */
const nbaId = z.number().int().positive();

/** {@link nbaId}, but normalized to `null` when the backend omits it entirely. */
const nullishNbaId = nbaId.nullish().transform((value) => value ?? null);

/**
 * A finite JSON number bounded to `[min, max]` whose value is exactly
 * expressible in thousandths (at most 3 decimal places of real precision) -
 * mirrors the backend's `Line`/`MinMinutes` value objects and their "0
 * through N, up to 3 decimal places" validation. Uses Zod's own numeric
 * constraints (`.finite()`, `.min()`, `.max()`, `.multipleOf(0.001)`) to
 * validate the *value*, not the original JSON token: a JSON number has
 * already lost any distinction between "24.5" and "24.5000" by the time
 * `JSON.parse` hands it to Zod (both become the identical IEEE-754 double
 * `24.5`), so this deliberately does not attempt to recover or judge
 * lexical/trailing-zero scale. The backend's own `BigDecimal` handling
 * remains the authoritative check on the raw JSON token; this schema only
 * needs to (and can) correctly enforce the numeric-value rule the frontend
 * actually cares about.
 */
function boundedThousandths(min: number, max: number) {
  return z.number().finite().min(min).max(max).multipleOf(0.001);
}

/** `line`: 0 through 999 inclusive, at most 3 decimal places. */
export const LineSchema = boundedThousandths(0, 999);

/** `minMinutes`: 0 through 80 inclusive, at most 3 decimal places. */
export const MinMinutesSchema = boundedThousandths(0, 80);

/** Loose ISO-8601 date check (LocalDate, e.g. "2024-10-22"). */
const isoDate = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "expected an ISO date (YYYY-MM-DD)");

/** Loose ISO-8601 instant check (java.time.Instant, e.g. "2025-03-02T08:15:30Z"). */
const isoInstant = z
  .string()
  .regex(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/,
    "expected an ISO instant",
  );

const nullishIsoInstant = isoInstant.nullish().transform((value) => value ?? null);

// ---------------------------------------------------------------------------
// Shared enums (exact string values from the backend's Java enums)
// ---------------------------------------------------------------------------

export const PropTypeSchema = z.enum([
  "POINTS",
  "REBOUNDS",
  "ASSISTS",
  "THREE_POINTERS_MADE",
  "PR",
  "PA",
  "RA",
  "PRA",
]);
export type PropType = z.infer<typeof PropTypeSchema>;

export const DirectionSchema = z.enum(["OVER", "UNDER"]);
export type Direction = z.infer<typeof DirectionSchema>;

export const LocationFilterSchema = z.enum(["HOME", "AWAY", "ALL"]);
export type LocationFilter = z.infer<typeof LocationFilterSchema>;

export const RecencyFilterSchema = z.enum(["LAST_5", "LAST_10", "LAST_20", "ALL"]);
export type RecencyFilter = z.infer<typeof RecencyFilterSchema>;

export const GameLocationSchema = z.enum(["HOME", "AWAY"]);
export type GameLocation = z.infer<typeof GameLocationSchema>;

export const GameResultSchema = z.enum(["HIT", "MISS", "PUSH"]);
export type GameResult = z.infer<typeof GameResultSchema>;

export const SampleQualitySchema = z.enum(["LOW", "MODERATE", "HIGH"]);
export type SampleQuality = z.infer<typeof SampleQualitySchema>;

export const DataStatusLevelSchema = z.enum(["READY", "EMPTY"]);
export type DataStatusLevel = z.infer<typeof DataStatusLevelSchema>;

// ---------------------------------------------------------------------------
// GET /api/players, GET /api/players/{nbaPlayerId} -> PlayerResponse
// ---------------------------------------------------------------------------

export const PlayerResponseSchema = z.object({
  nbaPlayerId: nbaId,
  firstName: z.string(),
  lastName: z.string(),
  fullName: z.string(),
  nbaTeamId: nullishNbaId,
  active: z.boolean(),
});
export type PlayerResponse = z.infer<typeof PlayerResponseSchema>;

export const PlayerListResponseSchema = z.array(PlayerResponseSchema);

// ---------------------------------------------------------------------------
// GET /api/teams -> TeamResponse[]
// ---------------------------------------------------------------------------

export const TeamResponseSchema = z.object({
  nbaTeamId: nbaId,
  abbreviation: z.string(),
  city: z.string(),
  nickname: z.string(),
  fullName: z.string(),
});
export type TeamResponse = z.infer<typeof TeamResponseSchema>;

export const TeamListResponseSchema = z.array(TeamResponseSchema);

// ---------------------------------------------------------------------------
// GET /api/props -> PropCatalogEntry[]
// ---------------------------------------------------------------------------

export const PropCatalogEntrySchema = z.object({
  code: z.string(),
  displayName: z.string(),
  componentStats: z.array(z.string()).min(1),
});
export type PropCatalogEntry = z.infer<typeof PropCatalogEntrySchema>;

export const PropCatalogResponseSchema = z.array(PropCatalogEntrySchema);

// ---------------------------------------------------------------------------
// GET /api/data/status -> DataStatusResponse
// ---------------------------------------------------------------------------

export const ImportRunSummarySchema = z.object({
  runId: z.number(),
  completedAt: isoInstant,
  recordsProcessed: z.number(),
  recordsFailed: z.number(),
});
export type ImportRunSummary = z.infer<typeof ImportRunSummarySchema>;

export const DataStatusCountsSchema = z.object({
  games: z.number(),
  playerGameStats: z.number(),
  players: z.number(),
  activePlayers: z.number(),
  teams: z.number(),
});
export type DataStatusCounts = z.infer<typeof DataStatusCountsSchema>;

export const DataStatusResponseSchema = z.object({
  dataImported: z.boolean(),
  status: DataStatusLevelSchema,
  latestGamesStatsImport: ImportRunSummarySchema.nullish().transform((value) => value ?? null),
  seasons: z.array(z.string()),
  counts: DataStatusCountsSchema,
});
export type DataStatusResponse = z.infer<typeof DataStatusResponseSchema>;

// ---------------------------------------------------------------------------
// POST /api/reports/matchup request body (outgoing)
// ---------------------------------------------------------------------------

export const MatchupReportRequestSchema = z.object({
  nbaPlayerId: nbaId,
  nbaOpponentTeamId: nbaId,
  prop: PropTypeSchema,
  line: LineSchema,
  direction: DirectionSchema,
  // Left as plain .nullish() (no null-collapsing transform, unlike the
  // response schemas above): the backend treats an omitted key and an
  // explicit JSON null identically, so whichever the caller provides is
  // passed straight through to JSON.stringify - omitted stays omitted,
  // explicit null stays null. Never invents a value the caller didn't set.
  season: z.string().nullish(),
  location: LocationFilterSchema.nullish(),
  recency: RecencyFilterSchema.nullish(),
  minMinutes: MinMinutesSchema.nullish(),
});
export type MatchupReportRequest = z.infer<typeof MatchupReportRequestSchema>;

// ---------------------------------------------------------------------------
// POST /api/reports/matchup response body (incoming) -> MatchupReportResponse
// ---------------------------------------------------------------------------

export const MatchupPlayerSchema = z.object({
  nbaPlayerId: nbaId,
  fullName: z.string(),
});

export const MatchupOpponentSchema = z.object({
  nbaTeamId: nbaId,
  abbreviation: z.string(),
  fullName: z.string(),
});

export const MatchupCriteriaSchema = z.object({
  player: MatchupPlayerSchema,
  opponent: MatchupOpponentSchema,
  prop: PropTypeSchema,
  line: z.number(),
  direction: DirectionSchema,
  seasonsApplied: z.array(z.string()),
  location: LocationFilterSchema,
  recency: RecencyFilterSchema,
  minMinutes: z.number(),
});

/**
 * hitRate/average/median are each independently nullable: hitRate is null
 * whenever hits + misses is 0 (zero qualifying games, or an all-push sample);
 * average/median are null only when qualifyingGames is 0. See
 * `SampleSummary`'s invariants in the backend.
 */
export const MatchupSampleSchema = z.object({
  qualifyingGames: z.number().int().nonnegative(),
  hits: z.number().int().nonnegative(),
  misses: z.number().int().nonnegative(),
  pushes: z.number().int().nonnegative(),
  hitRate: nullishNumber,
  average: nullishNumber,
  median: nullishNumber,
});

export const MatchupComparisonSchema = z.object({
  hitRateDifferencePoints: nullishNumber,
});

/**
 * completedAt is null only when no completed GAMES_STATS import run exists
 * yet (see `DataStatus` javadoc and the `NO_SUCCESSFUL_GAMES_STATS_IMPORT`
 * warning) - a real, tested backend state, not a merely defensive one.
 */
export const DataFreshnessSchema = z.object({
  importType: z.string(),
  completedAt: nullishIsoInstant,
});

export const OpponentContextSchema = z.object({
  available: z.boolean(),
  reason: z.string(),
});

export const MatchupGameSchema = z.object({
  nbaGameId: z.string(),
  gameDate: isoDate,
  season: z.string(),
  location: GameLocationSchema,
  nbaTeamId: nbaId,
  opponentNbaTeamId: nbaId,
  minutes: z.number(),
  propValue: z.number(),
  line: z.number(),
  result: GameResultSchema,
});

export const ChartPointSchema = z.object({
  nbaGameId: z.string(),
  gameDate: isoDate,
  propValue: z.number(),
  result: GameResultSchema,
});

export const MatchupReportResponseSchema = z.object({
  criteria: MatchupCriteriaSchema,
  matchup: MatchupSampleSchema,
  baseline: MatchupSampleSchema,
  comparison: MatchupComparisonSchema,
  sampleQuality: SampleQualitySchema.nullish().transform((value) => value ?? null),
  dataFreshness: DataFreshnessSchema,
  opponentContext: OpponentContextSchema,
  games: z.array(MatchupGameSchema),
  chart: z.array(ChartPointSchema),
  warnings: z.array(z.string()),
});
export type MatchupReportResponse = z.infer<typeof MatchupReportResponseSchema>;

// ---------------------------------------------------------------------------
// Error body -> ApiErrorResponse (see ApiErrorResponse.java/ApiFieldError.java)
// ---------------------------------------------------------------------------

export const ApiFieldErrorSchema = z.object({
  field: z.string(),
  code: z.string(),
  message: z.string(),
});
export type ApiFieldErrorBody = z.infer<typeof ApiFieldErrorSchema>;

export const ApiErrorResponseSchema = z.object({
  code: z.string(),
  message: z.string(),
  fieldErrors: z.array(ApiFieldErrorSchema),
  path: z.string(),
  timestamp: z.string(),
});
export type ApiErrorResponseBody = z.infer<typeof ApiErrorResponseSchema>;
