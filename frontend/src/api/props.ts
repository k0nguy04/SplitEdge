import { request, type RequestOptions } from "./client";
import { PropCatalogResponseSchema, type PropCatalogEntry } from "./schemas";
import { buildApiUrl } from "./url";

/** GET /api/props */
export function fetchPropCatalog(options: RequestOptions = {}): Promise<PropCatalogEntry[]> {
  return request(PropCatalogResponseSchema, {
    method: "GET",
    url: buildApiUrl(["props"]),
    signal: options.signal,
  });
}
