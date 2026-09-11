import { request, type RequestOptions } from "./client";
import { DataStatusResponseSchema, type DataStatusResponse } from "./schemas";
import { buildApiUrl } from "./url";

/** GET /api/data/status */
export function fetchDataStatus(options: RequestOptions = {}): Promise<DataStatusResponse> {
  return request(DataStatusResponseSchema, {
    method: "GET",
    url: buildApiUrl(["data", "status"]),
    signal: options.signal,
  });
}
