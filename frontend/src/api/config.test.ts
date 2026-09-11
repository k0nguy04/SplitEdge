import { afterEach, describe, expect, it, vi } from "vitest";

import { getApiBaseUrl } from "./config";

describe("getApiBaseUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("defaults to /api when VITE_API_BASE_URL is unset", () => {
    vi.stubEnv("VITE_API_BASE_URL", "");
    expect(getApiBaseUrl()).toBe("/api");
  });

  it("strips a single trailing slash (/api/)", () => {
    vi.stubEnv("VITE_API_BASE_URL", "/api/");
    expect(getApiBaseUrl()).toBe("/api");
  });

  it("strips repeated trailing slashes (/api///)", () => {
    vi.stubEnv("VITE_API_BASE_URL", "/api///");
    expect(getApiBaseUrl()).toBe("/api");
  });

  it("preserves an absolute origin with no trailing slash", () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8081");
    expect(getApiBaseUrl()).toBe("http://localhost:8081");
  });

  it("strips repeated trailing slashes from an absolute origin without corrupting the :// separator", () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8081///");
    expect(getApiBaseUrl()).toBe("http://localhost:8081");
  });
});
