const DEFAULT_API_BASE_URL = "/api";

/**
 * The browser-facing API base URL. Defaults to a same-origin `/api` path (see
 * `frontend/vite.config.ts`'s dev proxy and `.env.example`'s
 * `VITE_API_BASE_URL`); a future hosted deployment may set this to an
 * absolute origin instead. Never holds a secret - this value ends up in the
 * built client bundle.
 */
export function getApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  const base = configured && configured.length > 0 ? configured : DEFAULT_API_BASE_URL;
  // Strips every trailing slash (not just one), e.g. "/api///" -> "/api" and
  // "http://localhost:8081///" -> "http://localhost:8081". Only matches
  // trailing characters, so an origin's "://" is never touched.
  return base.replace(/\/+$/, "");
}
