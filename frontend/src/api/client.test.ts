import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";

import { request } from "./client";
import { ApiError, MalformedResponseError, NetworkError } from "./errors";

const Schema = z.object({ ok: z.boolean() });

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function textResponse(status: number, body: string): Response {
  return new Response(body, { status });
}

function emptyResponse(status: number): Response {
  return new Response(null, { status });
}

describe("request", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("parses a successful response through the schema", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(200, { ok: true })));

    const result = await request(Schema, { method: "GET", url: "/api/thing" });

    expect(result).toEqual({ ok: true });
  });

  it("never sends credentials by default", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await request(Schema, { method: "GET", url: "/api/thing" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/thing",
      expect.objectContaining({ credentials: "omit" }),
    );
  });

  it("passes an AbortSignal through to fetch", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await request(Schema, { method: "GET", url: "/api/thing", signal: controller.signal });

    expect(fetchMock).toHaveBeenCalledWith("/api/thing", expect.objectContaining({ signal: controller.signal }));
  });

  it("turns a non-2xx response with a valid ApiErrorResponse body into a typed ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(404, {
          code: "UNKNOWN_PLAYER",
          message: "no stored player has that NBA player ID",
          fieldErrors: [{ field: "nbaPlayerId", code: "UNKNOWN_PLAYER", message: "no stored player has that NBA player ID" }],
          path: "/api/players/1",
          timestamp: "2025-03-02T08:15:30Z",
        }),
      ),
    );

    const error = await request(Schema, { method: "GET", url: "/api/players/1" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(404);
    expect(apiError.code).toBe("UNKNOWN_PLAYER");
    expect(apiError.fieldErrors[0].field).toBe("nbaPlayerId");
  });

  it("turns a non-2xx response with a malformed/non-JSON body into a safe generic ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(textResponse(500, "<html>server error</html>")));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(500);
    expect(apiError.code).toBe("UNKNOWN_ERROR");
    expect(apiError.fieldErrors).toEqual([]);
  });

  it("turns a non-2xx response with an empty body into a safe generic ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(emptyResponse(503)));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(503);
    expect((error as ApiError).code).toBe("UNKNOWN_ERROR");
  });

  it("rejects a 2xx response whose body is not valid JSON with MalformedResponseError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(textResponse(200, "not json")));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(MalformedResponseError);
  });

  it("rejects a 2xx response whose body fails the schema with MalformedResponseError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(200, { ok: "not-a-boolean" })));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(MalformedResponseError);
  });

  it("does not silently accept an empty/204 body from an endpoint requiring JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(emptyResponse(204)));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(MalformedResponseError);
  });

  it("turns a fetch rejection (no HTTP response at all) into a NetworkError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(NetworkError);
  });

  it("rethrows an AbortError unchanged instead of wrapping it as a NetworkError", async () => {
    const abortError = new DOMException("The operation was aborted", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(abortError));

    const error = await request(Schema, { method: "GET", url: "/api/thing" }).catch((e: unknown) => e);

    expect(error).toBe(abortError);
    expect(error).not.toBeInstanceOf(NetworkError);
    expect(error).not.toBeInstanceOf(ApiError);
    expect((error as DOMException).name).toBe("AbortError");
  });
});
