from types import SimpleNamespace

from splitedge_importer.retrieval.nba_api_client import _reraise_http_error, _rows_from_nba_dict
from splitedge_importer.retrieval.retry import NonRetryableHttpError, RetryableError


def test_rows_from_result_sets() -> None:
    payload = {
        "resultSets": [
            {
                "headers": ["PERSON_ID", "DISPLAY_FIRST_LAST"],
                "rowSet": [[201939, "Stephen Curry"]],
            }
        ]
    }
    rows = _rows_from_nba_dict(payload)
    assert rows == [{"PERSON_ID": 201939, "DISPLAY_FIRST_LAST": "Stephen Curry"}]


def test_reraise_429_is_retryable() -> None:
    exc = Exception("too many requests")
    exc.response = SimpleNamespace(status_code=429, headers={"Retry-After": "2"})  # type: ignore[attr-defined]
    try:
        _reraise_http_error(exc)
    except RetryableError as retryable:
        assert retryable.retry_after == "2"
    else:
        raise AssertionError("expected RetryableError")


def test_reraise_404_is_not_retryable() -> None:
    response = SimpleNamespace(status_code=404, headers={})
    exc = Exception("not found")
    exc.response = response  # type: ignore[attr-defined]
    try:
        _reraise_http_error(exc)
    except NonRetryableHttpError as err:
        assert err.status_code == 404
    else:
        raise AssertionError("expected NonRetryableHttpError")
