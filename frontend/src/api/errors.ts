import type { ApiFieldErrorBody } from "./schemas";

/**
 * The backend explicitly returned a non-2xx response with a (successfully
 * parsed) `ApiErrorResponse` body, or a non-2xx response whose body could not
 * be read/parsed at all (in which case `code` falls back to `"UNKNOWN_ERROR"`
 * and `fieldErrors` is empty) - either way, the server actively rejected the
 * request. Distinct from {@link MalformedResponseError}, which means the
 * server reported success but the body did not match the expected shape.
 */
export class ApiError extends Error {
  readonly kind = "api" as const;
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldErrorBody[];

  constructor(status: number, code: string, message: string, fieldErrors: ApiFieldErrorBody[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * The HTTP response reported success (2xx) but the body was not valid JSON,
 * was unexpectedly empty (e.g. a 204 from an endpoint that must return JSON),
 * or did not match the expected Zod schema. This is always a client-side
 * "the response was not what we expected" case, never something the server
 * told us was wrong with the request.
 */
export class MalformedResponseError extends Error {
  readonly kind = "malformed" as const;
  readonly status?: number;
  readonly cause?: unknown;

  constructor(message: string, status?: number, cause?: unknown) {
    super(message);
    this.name = "MalformedResponseError";
    this.status = status;
    this.cause = cause;
  }
}

/**
 * The caller passed an invalid runtime argument to an API function itself
 * (for example, an out-of-range `limit`) - the request was never sent, so
 * this is neither a {@link NetworkError} (fetch never ran) nor an
 * {@link ApiError} (the backend never had a chance to reject anything). Kept
 * distinct so a local, pre-flight validation mistake is never misclassified
 * as a backend or network failure.
 */
export class InvalidArgumentError extends Error {
  readonly kind = "invalidArgument" as const;

  constructor(message: string) {
    super(message);
    this.name = "InvalidArgumentError";
  }
}

/**
 * `fetch` itself rejected (offline, DNS failure, connection refused, etc.)
 * rather than returning an HTTP response at all. Distinct from both
 * {@link ApiError} (the server responded, but with an error) and an
 * {@link !DOMException} abort (the caller cancelled the request; see
 * {@link isAbortError}, which must be checked before treating a fetch
 * rejection as a `NetworkError`).
 */
export class NetworkError extends Error {
  readonly kind = "network" as const;
  readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = "NetworkError";
    this.cause = cause;
  }
}

/**
 * True when `error` is the `AbortError` a browser `fetch` throws when its
 * `AbortSignal` fires. Callers must check this before wrapping a fetch
 * rejection in {@link NetworkError} so a deliberate cancellation is never
 * reported as a backend failure.
 */
export function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === "AbortError") ||
    (error instanceof Error && error.name === "AbortError")
  );
}
