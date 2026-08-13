"""Retry helper for transient NBA HTTP failures."""

from __future__ import annotations

import random
import time
from collections.abc import Callable
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime

RETRYABLE_STATUS_CODES = frozenset({429, 500, 502, 503, 504})


class RetryableError(Exception):
    """Transient error that should be retried."""

    def __init__(self, message: str, retry_after: str | None = None) -> None:
        super().__init__(message)
        self.retry_after = retry_after


class NonRetryableHttpError(Exception):
    """HTTP 4xx other than 429; do not retry."""

    def __init__(self, status_code: int, message: str | None = None) -> None:
        super().__init__(message or f"HTTP {status_code}")
        self.status_code = status_code


def is_retryable_status(status_code: int) -> bool:
    return status_code in RETRYABLE_STATUS_CODES or status_code >= 500


def parse_retry_after(value: str | None, *, now: datetime | None = None) -> float | None:
    if value is None or not value.strip():
        return None
    raw = value.strip()
    if raw.isdigit():
        return float(raw)
    try:
        retry_at = parsedate_to_datetime(raw)
    except (TypeError, ValueError):
        return None
    if retry_at.tzinfo is None:
        retry_at = retry_at.replace(tzinfo=UTC)
    current = now or datetime.now(UTC)
    delay = (retry_at - current).total_seconds()
    return max(delay, 0.0)


def retry_call[T](
    fn: Callable[[], T],
    *,
    max_attempts: int = 5,
    base_delay_seconds: float = 0.5,
    max_delay_seconds: float = 8.0,
    sleep: Callable[[float], None] = time.sleep,
    rng: Callable[[], float] = random.random,
    clock: Callable[[], datetime] | None = None,
) -> T:
    """Retry ``fn`` on timeouts, connection errors, 5xx, and 429."""
    last_error: BaseException | None = None
    attempts = max(1, max_attempts)
    for attempt in range(attempts):
        try:
            return fn()
        except NonRetryableHttpError:
            raise
        except RetryableError as exc:
            last_error = exc
            if attempt >= attempts - 1:
                break
            delay = _compute_delay(
                attempt=attempt,
                base_delay_seconds=base_delay_seconds,
                max_delay_seconds=max_delay_seconds,
                retry_after=exc.retry_after,
                rng=rng,
                clock=clock,
            )
            sleep(delay)
        except Exception as exc:
            if not _is_timeout_or_connection(exc):
                raise
            last_error = exc
            if attempt >= attempts - 1:
                break
            delay = _compute_delay(
                attempt=attempt,
                base_delay_seconds=base_delay_seconds,
                max_delay_seconds=max_delay_seconds,
                retry_after=None,
                rng=rng,
                clock=clock,
            )
            sleep(delay)
    assert last_error is not None
    raise last_error


def _compute_delay(
    *,
    attempt: int,
    base_delay_seconds: float,
    max_delay_seconds: float,
    retry_after: str | None,
    rng: Callable[[], float],
    clock: Callable[[], datetime] | None,
) -> float:
    exponential = min(max_delay_seconds, base_delay_seconds * (2**attempt))
    jittered = exponential * (0.5 + (rng() * 0.5))
    header_delay = parse_retry_after(
        retry_after,
        now=clock() if clock is not None else None,
    )
    if header_delay is None:
        return jittered
    return min(max_delay_seconds, max(jittered, header_delay))


def _is_timeout_or_connection(exc: BaseException) -> bool:
    if isinstance(exc, (TimeoutError, ConnectionError)):
        return True
    name = type(exc).__name__
    return name in {"Timeout", "ConnectTimeout", "ReadTimeout", "ConnectionError"}
