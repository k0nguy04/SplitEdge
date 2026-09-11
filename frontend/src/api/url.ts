import { getApiBaseUrl } from "./config";

export type QueryValue = string | number | boolean | undefined;

/**
 * Builds an API URL from path segments and optional query parameters without
 * manual string concatenation. Each path segment is percent-encoded via
 * `encodeURIComponent`, and query parameters are built with
 * `URLSearchParams`, so callers never need to escape values themselves.
 * `undefined` query values are omitted entirely (not sent as the literal
 * string `"undefined"`).
 */
export function buildApiUrl(pathSegments: string[], query?: Record<string, QueryValue>): string {
  const base = getApiBaseUrl();
  const path = [base, ...pathSegments.map((segment) => encodeURIComponent(segment))].join("/");

  if (!query) {
    return path;
  }

  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      searchParams.set(key, String(value));
    }
  }

  const queryString = searchParams.toString();
  return queryString.length > 0 ? `${path}?${queryString}` : path;
}
