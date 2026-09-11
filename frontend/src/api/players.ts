import { z } from "zod";

import { request, type RequestOptions } from "./client";
import { InvalidArgumentError } from "./errors";
import { PlayerListResponseSchema, PlayerResponseSchema, type PlayerResponse } from "./schemas";
import { buildApiUrl } from "./url";

const DEFAULT_ACTIVE_PLAYER_LIMIT = 600;

/** Mirrors the backend's own bounds (`PlayerController.MIN/MAX_ACTIVE_LIMIT`). */
const ActivePlayerLimitSchema = z.number().int().min(1).max(1000);

/**
 * Resolves and validates `limit` before any request is sent. `undefined`
 * resolves to the same default the backend itself applies (600); any other
 * value must be a finite integer from 1 through 1000, checked with a real
 * runtime schema rather than trusting the `number` type annotation alone (a
 * caller can pass `NaN`, `Infinity`, or a non-integer at runtime despite the
 * TypeScript type). An invalid value throws {@link InvalidArgumentError}
 * synchronously - `fetchActivePlayers` is declared `async` specifically so
 * that throw becomes a rejected promise rather than a synchronous exception,
 * and so it is never sent to `fetch` or misclassified as a network/API
 * failure.
 */
function resolveActivePlayerLimit(limit: number | undefined): number {
  if (limit === undefined) {
    return DEFAULT_ACTIVE_PLAYER_LIMIT;
  }
  const parsed = ActivePlayerLimitSchema.safeParse(limit);
  if (!parsed.success) {
    throw new InvalidArgumentError(
      `limit must be a finite integer from 1 through 1000 (received ${JSON.stringify(limit)})`,
    );
  }
  return parsed.data;
}

/**
 * GET /api/players?limit=<n> - active players only, deterministically
 * ordered by the backend. `limit` is optional and defaults to 600; when
 * provided, it is validated (finite integer, 1-1000) before any request is
 * sent, and the resolved value is always encoded numerically exactly once.
 */
export async function fetchActivePlayers(
  limit?: number,
  options: RequestOptions = {},
): Promise<PlayerResponse[]> {
  const resolvedLimit = resolveActivePlayerLimit(limit);
  return request(PlayerListResponseSchema, {
    method: "GET",
    url: buildApiUrl(["players"], { limit: resolvedLimit }),
    signal: options.signal,
  });
}

/** GET /api/players/{nbaPlayerId} - includes inactive/historical players. */
export function fetchPlayerById(nbaPlayerId: number, options: RequestOptions = {}): Promise<PlayerResponse> {
  return request(PlayerResponseSchema, {
    method: "GET",
    url: buildApiUrl(["players", String(nbaPlayerId)]),
    signal: options.signal,
  });
}
