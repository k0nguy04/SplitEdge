"""Live nba_api adapter. This module is the only one allowed to import nba_api."""

from __future__ import annotations

from collections.abc import Callable
from typing import Any, NoReturn

from splitedge_importer.retrieval.retry import (
    NonRetryableHttpError,
    RetryableError,
    is_retryable_status,
    retry_call,
)


def _rows_from_nba_dict(payload: dict[str, Any]) -> list[dict]:
    result_sets = payload.get("resultSets")
    if not result_sets:
        normalized = payload.get("CommonAllPlayers")
        if isinstance(normalized, list):
            return [dict(row) for row in normalized]
        raise RetryableError("nba_api CommonAllPlayers payload missing resultSets")
    first = result_sets[0]
    headers = first.get("headers") or []
    row_set = first.get("rowSet") or []
    return [dict(zip(headers, row, strict=False)) for row in row_set]


class NbaApiClient:
    """Fetches teams and active players through nba_api with retry/backoff."""

    def __init__(
        self,
        *,
        timeout_seconds: float = 30.0,
        retry_max_attempts: int = 5,
        retry_base_delay_seconds: float = 0.5,
        retry_max_delay_seconds: float = 8.0,
        retry: Callable[..., Any] | None = None,
    ) -> None:
        self._timeout_seconds = timeout_seconds
        self._retry_max_attempts = retry_max_attempts
        self._retry_base_delay_seconds = retry_base_delay_seconds
        self._retry_max_delay_seconds = retry_max_delay_seconds
        self._retry = retry or retry_call

    def fetch_teams(self) -> list[dict]:
        from nba_api.stats.static import teams as nba_teams

        return [dict(team) for team in nba_teams.get_teams()]

    def fetch_active_players(self, season: str) -> list[dict]:
        def _call() -> list[dict]:
            from nba_api.stats.endpoints import commonallplayers
            from nba_api.stats.library.http import NBAStatsHTTP

            original = NBAStatsHTTP.send_api_request

            def guarded_send(http_self: Any, *args: Any, **kwargs: Any) -> Any:
                kwargs = dict(kwargs)
                kwargs.setdefault("timeout", self._timeout_seconds)
                session = http_self.get_session()
                original_get = session.get

                def get_with_status(*get_args: Any, **get_kwargs: Any) -> Any:
                    get_kwargs.setdefault("timeout", self._timeout_seconds)
                    try:
                        response = original_get(*get_args, **get_kwargs)
                    except Exception as exc:
                        _reraise_http_error(exc)
                    status = response.status_code
                    if status != 200:
                        error = Exception(f"HTTP {status}")
                        error.response = response  # type: ignore[attr-defined]
                        _reraise_http_error(error)
                    return response

                session.get = get_with_status
                try:
                    return original(http_self, *args, **kwargs)
                except (RetryableError, NonRetryableHttpError):
                    raise
                except Exception as exc:
                    _reraise_http_error(exc)
                finally:
                    session.get = original_get

            NBAStatsHTTP.send_api_request = guarded_send  # type: ignore[method-assign]
            try:
                endpoint = commonallplayers.CommonAllPlayers(
                    is_only_current_season=1,
                    league_id="00",
                    season=season,
                    timeout=self._timeout_seconds,
                )
                return _rows_from_nba_dict(endpoint.get_dict())
            finally:
                NBAStatsHTTP.send_api_request = original  # type: ignore[method-assign]

        return self._retry(
            _call,
            max_attempts=self._retry_max_attempts,
            base_delay_seconds=self._retry_base_delay_seconds,
            max_delay_seconds=self._retry_max_delay_seconds,
        )


def _reraise_http_error(exc: BaseException) -> NoReturn:
    status = _status_code(exc)
    retry_after = _header(exc, "Retry-After")
    if status is None:
        if _is_timeout_or_connection(exc):
            raise RetryableError(str(exc)) from exc
        raise exc
    if is_retryable_status(status):
        raise RetryableError(f"HTTP {status}", retry_after=retry_after) from exc
    if 400 <= status < 500:
        raise NonRetryableHttpError(status) from exc
    raise RetryableError(f"HTTP {status}", retry_after=retry_after) from exc


def _status_code(exc: BaseException) -> int | None:
    response = getattr(exc, "response", None)
    if response is not None:
        code = getattr(response, "status_code", None)
        if isinstance(code, int):
            return code
    status = getattr(exc, "status_code", None)
    return status if isinstance(status, int) else None


def _header(exc: BaseException, name: str) -> str | None:
    response = getattr(exc, "response", None)
    if response is None:
        return None
    headers = getattr(response, "headers", None)
    if headers is None:
        return None
    try:
        value = headers.get(name)
    except Exception:
        return None
    return str(value) if value is not None else None


def _is_timeout_or_connection(exc: BaseException) -> bool:
    if isinstance(exc, (TimeoutError, ConnectionError)):
        return True
    name = type(exc).__name__
    return name in {"Timeout", "ConnectTimeout", "ReadTimeout", "ConnectionError"}
