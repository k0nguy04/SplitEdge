from datetime import UTC, datetime

from splitedge_importer.retrieval.retry import (
    NonRetryableHttpError,
    RetryableError,
    parse_retry_after,
    retry_call,
)


def test_retries_timeout_then_succeeds() -> None:
    calls = {"count": 0}

    def flaky() -> str:
        calls["count"] += 1
        if calls["count"] < 3:
            raise TimeoutError("timed out")
        return "ok"

    sleeps: list[float] = []
    assert retry_call(flaky, max_attempts=5, sleep=sleeps.append, rng=lambda: 0.0) == "ok"
    assert calls["count"] == 3
    assert sleeps


def test_retries_429_and_honors_retry_after() -> None:
    calls = {"count": 0}

    def flaky() -> str:
        calls["count"] += 1
        if calls["count"] == 1:
            raise RetryableError("HTTP 429", retry_after="1")
        return "ok"

    sleeps: list[float] = []
    assert retry_call(flaky, max_attempts=3, sleep=sleeps.append, rng=lambda: 0.0) == "ok"
    assert sleeps[0] >= 1.0


def test_retries_5xx() -> None:
    calls = {"count": 0}

    def flaky() -> str:
        calls["count"] += 1
        if calls["count"] == 1:
            raise RetryableError("HTTP 503")
        return "ok"

    retry_call(flaky, max_attempts=3, sleep=lambda _delay: None, rng=lambda: 0.0)
    assert calls["count"] == 2


def test_does_not_retry_other_4xx() -> None:
    calls = {"count": 0}

    def bad() -> str:
        calls["count"] += 1
        raise NonRetryableHttpError(404)

    try:
        retry_call(bad, max_attempts=5, sleep=lambda _delay: None)
    except NonRetryableHttpError as exc:
        assert exc.status_code == 404
    else:
        raise AssertionError("expected NonRetryableHttpError")
    assert calls["count"] == 1


def test_exhausted_retries_raise_last_error() -> None:
    def always_fail() -> str:
        raise RetryableError("HTTP 500")

    try:
        retry_call(always_fail, max_attempts=3, sleep=lambda _delay: None, rng=lambda: 0.0)
    except RetryableError as exc:
        assert "500" in str(exc)
    else:
        raise AssertionError("expected RetryableError")


def test_parse_retry_after_http_date() -> None:
    now = datetime(2026, 8, 13, 16, 0, tzinfo=UTC)
    delay = parse_retry_after("Thu, 13 Aug 2026 16:00:05 GMT", now=now)
    assert delay == 5.0
