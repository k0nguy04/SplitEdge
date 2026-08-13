"""Importer configuration loaded from the environment."""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from urllib.parse import unquote, urlparse

SEASON_PATTERN = re.compile(r"^\d{4}-\d{2}$")


class ConfigError(ValueError):
    """Raised when required importer configuration is missing or invalid."""


@dataclass(frozen=True)
class Config:
    database_url: str
    nba_season: str
    min_teams: int = 30
    min_active_players: int = 300
    http_timeout_seconds: float = 30.0
    retry_max_attempts: int = 5
    retry_base_delay_seconds: float = 0.5
    retry_max_delay_seconds: float = 8.0

    @property
    def password(self) -> str | None:
        return _password_from_url(self.database_url)


def load_config(environ: dict[str, str] | None = None) -> Config:
    env = os.environ if environ is None else environ
    database_url = (env.get("DATABASE_URL") or "").strip()
    nba_season = (env.get("NBA_SEASON") or "").strip()

    missing: list[str] = []
    if not database_url:
        missing.append("DATABASE_URL")
    if not nba_season:
        missing.append("NBA_SEASON")
    if missing:
        raise ConfigError("Missing required environment variable(s): " + ", ".join(missing))

    if SEASON_PATTERN.fullmatch(nba_season) is None:
        raise ConfigError("NBA_SEASON must match YYYY-YY, for example 2025-26")

    return Config(
        database_url=database_url,
        nba_season=nba_season,
        min_teams=_optional_positive_int(env, "IMPORT_MIN_TEAMS", 30),
        min_active_players=_optional_positive_int(env, "IMPORT_MIN_ACTIVE_PLAYERS", 300),
        http_timeout_seconds=_optional_float(env, "NBA_HTTP_TIMEOUT_SECONDS", 30.0),
        retry_max_attempts=_optional_positive_int(env, "NBA_RETRY_MAX_ATTEMPTS", 5),
        retry_base_delay_seconds=_optional_float(env, "NBA_RETRY_BASE_DELAY_SECONDS", 0.5),
        retry_max_delay_seconds=_optional_float(env, "NBA_RETRY_MAX_DELAY_SECONDS", 8.0),
    )


def _optional_positive_int(env: dict[str, str], name: str, default: int) -> int:
    raw = (env.get(name) or "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be a positive integer") from exc
    if value < 1:
        raise ConfigError(f"{name} must be a positive integer")
    return value


def _optional_float(env: dict[str, str], name: str, default: float) -> float:
    raw = (env.get(name) or "").strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be a number") from exc
    if value <= 0:
        raise ConfigError(f"{name} must be greater than zero")
    return value


def _password_from_url(database_url: str) -> str | None:
    parsed = urlparse(database_url)
    if not parsed.password:
        return None
    return unquote(parsed.password)
