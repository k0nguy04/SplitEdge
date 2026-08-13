"""Immutable importer domain models."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from typing import Any


@dataclass(frozen=True)
class NormalizedTeam:
    nba_team_id: int
    abbreviation: str
    full_name: str
    nickname: str
    city: str


@dataclass(frozen=True)
class NormalizedPlayer:
    nba_player_id: int
    first_name: str
    last_name: str
    full_name: str
    is_active: bool
    nba_team_id: int | None
    missing_team: bool = False


@dataclass(frozen=True)
class AmbiguousGame:
    nba_game_id: str
    season: str
    game_date: date
    team_rows: tuple[dict[str, Any], dict[str, Any]]


@dataclass(frozen=True)
class NormalizedGame:
    nba_game_id: str
    season: str
    game_date: date
    home_nba_team_id: int
    away_nba_team_id: int
    home_score: int
    away_score: int
    status: str = "FINAL"


@dataclass(frozen=True)
class NormalizedPlayerGameStat:
    nba_game_id: str
    season: str
    nba_player_id: int
    nba_team_id: int
    minutes: Decimal
    points: int
    rebounds: int
    assists: int
    three_pointers_made: int
    player_name: str


@dataclass(frozen=True)
class HistoricalPlayerStub:
    nba_player_id: int
    first_name: str
    last_name: str
    full_name: str


@dataclass(frozen=True)
class CheckpointRecord:
    import_type: str
    season: str
    resource: str
    status: str
    row_count: int
    payload: Any = None


@dataclass(frozen=True)
class RejectedRecord:
    reason: str
    entity: str
    raw: dict[str, Any]
    season: str | None = None
    nba_game_id: str | None = None


@dataclass(frozen=True)
class WarningRecord:
    reason: str
    entity: str
    nba_player_id: int
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class EntityCounts:
    received: int = 0
    persisted: int = 0
    rejected: int = 0
    warning: int = 0
    deactivation: int = 0

    def as_dict(self) -> dict[str, int]:
        return {
            "received": self.received,
            "persisted": self.persisted,
            "rejected": self.rejected,
            "warning": self.warning,
            "deactivation": self.deactivation,
        }


@dataclass(frozen=True)
class GuardResult:
    passed: bool
    reason: str | None = None


@dataclass(frozen=True)
class ImportResult:
    success: bool
    run_id: int | None = None
    status: str | None = None
    records_processed: int = 0
    records_failed: int = 0
    details: dict[str, Any] = field(default_factory=dict)
    error_message: str | None = None
