import { request, type RequestOptions } from "./client";
import { TeamListResponseSchema, type TeamResponse } from "./schemas";
import { buildApiUrl } from "./url";

/** GET /api/teams */
export function fetchTeams(options: RequestOptions = {}): Promise<TeamResponse[]> {
  return request(TeamListResponseSchema, {
    method: "GET",
    url: buildApiUrl(["teams"]),
    signal: options.signal,
  });
}
