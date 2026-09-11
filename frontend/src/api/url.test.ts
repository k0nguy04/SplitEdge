import { afterEach, describe, expect, it, vi } from "vitest";

import { buildApiUrl } from "./url";

describe("buildApiUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("joins path segments under the configured API base", () => {
    expect(buildApiUrl(["players"])).toBe("/api/players");
    expect(buildApiUrl(["players", "201939"])).toBe("/api/players/201939");
    expect(buildApiUrl(["reports", "matchup"])).toBe("/api/reports/matchup");
  });

  it("does not duplicate the /api prefix under the default base", () => {
    const url = buildApiUrl(["players"]);
    expect(url).toBe("/api/players");
    expect(url).not.toContain("/api/api");
  });

  it("does not duplicate the /api prefix when the base is an absolute origin ending in /api", () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://backend.example.com/api");
    const url = buildApiUrl(["players", "201939"]);
    expect(url).toBe("https://backend.example.com/api/players/201939");
    expect(url).not.toContain("/api/api");
  });

  it("percent-encodes path segments", () => {
    expect(buildApiUrl(["te am"])).toBe("/api/te%20am");
  });

  it("encodes a numeric query value as a plain number, not a quoted string", () => {
    expect(buildApiUrl(["players"], { limit: 50 })).toBe("/api/players?limit=50");
  });

  it("omits query parameters whose value is undefined", () => {
    expect(buildApiUrl(["players"], { limit: undefined })).toBe("/api/players");
  });

  it("safely encodes special characters in query values", () => {
    const url = buildApiUrl(["players"], { query: "a b&c" });
    expect(url).toBe("/api/players?query=a+b%26c");
  });

  it("adds no trailing question mark when no query parameters are provided", () => {
    expect(buildApiUrl(["props"], {})).toBe("/api/props");
  });

  it("encodes query values exactly once, even with an absolute base configured", () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://backend.example.com/api/");
    const url = buildApiUrl(["players"], { query: "a b&c" });
    expect(url).toBe("https://backend.example.com/api/players?query=a+b%26c");
    // A double-encode would turn "%20"/"%26" into "%2520"/"%2526"; assert the
    // literal single-encoded form appears and nothing double-escaped does.
    expect(url).not.toContain("%25");
  });
});
