import { request, type RequestOptions } from "./client";
import {
  MatchupReportRequestSchema,
  MatchupReportResponseSchema,
  type MatchupReportRequest,
  type MatchupReportResponse,
} from "./schemas";
import { buildApiUrl } from "./url";

/**
 * POST /api/reports/matchup. The outgoing request is parsed through
 * `MatchupReportRequestSchema` first, both to validate it and to strip any
 * unexpected extra properties before it is serialized. Optional filters the
 * caller leaves `undefined` stay `undefined` (so `JSON.stringify` omits the
 * key entirely) rather than being coerced into an explicit `null`.
 */
export async function fetchMatchupReport(
  body: MatchupReportRequest,
  options: RequestOptions = {},
): Promise<MatchupReportResponse> {
  const validated = MatchupReportRequestSchema.parse(body);
  return request(MatchupReportResponseSchema, {
    method: "POST",
    url: buildApiUrl(["reports", "matchup"]),
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(validated),
    signal: options.signal,
  });
}
