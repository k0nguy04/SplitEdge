import type { z } from "zod";

import { ApiError, MalformedResponseError, NetworkError, isAbortError } from "./errors";
import { ApiErrorResponseSchema } from "./schemas";

export interface RequestOptions {
  signal?: AbortSignal;
}

interface RequestInit {
  method: "GET" | "POST";
  url: string;
  body?: string;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

const FALLBACK_ERROR_CODE = "UNKNOWN_ERROR";

/**
 * Performs one API request and parses its JSON body through `schema`.
 *
 * - Non-2xx responses are turned into a rejected promise carrying an
 *   {@link ApiError} (the server actively rejected the request).
 * - A 2xx response with an empty, non-JSON, or schema-invalid body is turned
 *   into a rejected promise carrying a {@link MalformedResponseError} (the
 *   server reported success but sent something unexpected).
 * - `fetch` itself failing (no HTTP response at all) is turned into a
 *   {@link NetworkError}, unless the failure was `signal` firing, in which
 *   case the original `AbortError` is rethrown unchanged so callers can keep
 *   distinguishing a deliberate cancellation from every other failure mode.
 */
export async function request<T>(schema: z.ZodType<T>, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(init.url, {
      method: init.method,
      body: init.body,
      // Explicit, not just relying on the browser default: this client never
      // sends cookies or other ambient credentials.
      credentials: "omit",
      headers: {
        Accept: "application/json",
        ...init.headers,
      },
      signal: init.signal,
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new NetworkError(`network request to ${init.url} failed`, error);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  const text = await response.text();
  if (text.length === 0) {
    throw new MalformedResponseError(
      `expected a JSON response body from ${init.url} but received an empty body (status ${response.status})`,
      response.status,
    );
  }

  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch (error) {
    throw new MalformedResponseError(`response from ${init.url} was not valid JSON`, response.status, error);
  }

  const parsed = schema.safeParse(json);
  if (!parsed.success) {
    throw new MalformedResponseError(
      `response from ${init.url} did not match the expected shape: ${parsed.error.message}`,
      response.status,
      parsed.error,
    );
  }

  return parsed.data;
}

async function toApiError(response: Response): Promise<ApiError> {
  let text: string;
  try {
    text = await response.text();
  } catch {
    return new ApiError(response.status, FALLBACK_ERROR_CODE, `request failed with status ${response.status}`);
  }

  if (text.length === 0) {
    return new ApiError(response.status, FALLBACK_ERROR_CODE, `request failed with status ${response.status}`);
  }

  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return new ApiError(response.status, FALLBACK_ERROR_CODE, `request failed with status ${response.status}`);
  }

  const parsed = ApiErrorResponseSchema.safeParse(json);
  if (!parsed.success) {
    return new ApiError(response.status, FALLBACK_ERROR_CODE, `request failed with status ${response.status}`);
  }

  return new ApiError(response.status, parsed.data.code, parsed.data.message, parsed.data.fieldErrors);
}
