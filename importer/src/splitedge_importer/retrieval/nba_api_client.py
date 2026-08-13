"""Live nba_api adapter. This module is the only one allowed to import nba_api."""

from __future__ import annotations

import time
from collections.abc import Callable
from typing import Any, NoReturn

from splitedge_importer.retrieval.league_game_log import required_columns_for
from splitedge_importer.retrieval.retry import (
    NonRetryableHttpError,
    RetryableError,
    is_retryable_status,
    retry_call,
)

REGULAR_SEASON = "Regular Season"


class MalformedNbaPayload(ValueError):
    """HTTP 200 body that is empty or missing required LeagueGameLog columns."""


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


def _rows_from_league_game_log(payload: dict[str, Any], *, resource: str) -> list[dict]:
    result_sets = payload.get("resultSets")
    if not result_sets:
        raise MalformedNbaPayload("LeagueGameLog payload missing resultSets")
    first = result_sets[0]
    headers = first.get("headers") or []
    row_set = first.get("rowSet") or []
    if not headers:
        raise MalformedNbaPayload("LeagueGameLog payload missing headers")
    if not row_set:
        raise MalformedNbaPayload("LeagueGameLog payload is empty")
    rows = [dict(zip(headers, row, strict=False)) for row in row_set]
    required = required_columns_for(resource)
    for row in rows:
        if not required.issubset(row.keys()):
            raise MalformedNbaPayload(f"LeagueGameLog {resource} is missing required columns")
    return rows


class NbaApiClient:
    """Fetches NBA Stats payloads through nba_api with retry/backoff and pacing."""

    def __init__(
        self,
        *,
        timeout_seconds: float = 30.0,
        retry_max_attempts: int = 5,
        retry_base_delay_seconds: float = 0.5,
        retry_max_delay_seconds: float = 8.0,
        request_interval_seconds: float = 0.6,
        retry: Callable[..., Any] | None = None,
        sleep: Callable[[float], None] = time.sleep,
        monotonic: Callable[[], float] = time.monotonic,
    ) -> None:
        self._timeout_seconds = timeout_seconds
        self._retry_max_attempts = retry_max_attempts
        self._retry_base_delay_seconds = retry_base_delay_seconds
        self._retry_max_delay_seconds = retry_max_delay_seconds
        self._request_interval_seconds = request_interval_seconds
        self._retry = retry or retry_call
        self._sleep = sleep
        self._monotonic = monotonic
        self._last_request_at: float | None = None

    def fetch_teams(self) -> list[dict]:
        from nba_api.stats.static import teams as nba_teams

        return [dict(team) for team in nba_teams.get_teams()]

    def fetch_active_players(self, season: str) -> list[dict]:
        def _build() -> Any:
            from nba_api.stats.endpoints import commonallplayers

            return commonallplayers.CommonAllPlayers(
                is_only_current_season=1,
                league_id="00",
                season=season,
                timeout=self._timeout_seconds,
            )

        return self._request_rows(_build, parser=_rows_from_nba_dict)

    def fetch_team_game_log(self, season: str) -> list[dict]:
        return self._fetch_league_game_log(season, player_or_team="T", resource="team_game_log")

    def fetch_player_game_log(self, season: str) -> list[dict]:
        return self._fetch_league_game_log(season, player_or_team="P", resource="player_game_log")

    def _fetch_league_game_log(
        self,
        season: str,
        *,
        player_or_team: str,
        resource: str,
    ) -> list[dict]:
        def _build() -> Any:
            from nba_api.stats.endpoints import leaguegamelog

            return leaguegamelog.LeagueGameLog(
                season=season,
                season_type_all_star=REGULAR_SEASON,
                player_or_team_abbreviation=player_or_team,
                league_id="00",
                timeout=self._timeout_seconds,
            )

        def _parse(payload: dict[str, Any]) -> list[dict]:
            return _rows_from_league_game_log(payload, resource=resource)

        return self._request_rows(_build, parser=_parse)

    def _request_rows(
        self,
        build_endpoint: Callable[[], Any],
        *,
        parser: Callable[[dict[str, Any]], list[dict]],
    ) -> list[dict]:
        def _call() -> list[dict]:
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
                endpoint = build_endpoint()
                return parser(endpoint.get_dict())
            finally:
                NBAStatsHTTP.send_api_request = original  # type: ignore[method-assign]

        rows = self._retry(
            _call,
            max_attempts=self._retry_max_attempts,
            base_delay_seconds=self._retry_base_delay_seconds,
            max_delay_seconds=self._retry_max_delay_seconds,
        )
        self._pace()
        return rows

    def _pace(self) -> None:
        now = self._monotonic()
        if self._last_request_at is not None:
            wait = self._request_interval_seconds - (now - self._last_request_at)
            if wait > 0:
                self._sleep(wait)
                now = self._monotonic()
        self._last_request_at = now


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
