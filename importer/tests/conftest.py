"""Shared test doubles and fixture loaders."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import pytest

from splitedge_importer.models import (
    CheckpointRecord,
    HistoricalPlayerStub,
    NormalizedGame,
    NormalizedPlayer,
    NormalizedPlayerGameStat,
    NormalizedTeam,
)
from splitedge_importer.persistence.db import jdbc_url, url_password, url_user

FIXTURES = Path(__file__).resolve().parent / "fixtures"
REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_POM = REPO_ROOT / "backend" / "pom.xml"

KNOWN_TEAM_IDS = {1610612744, 1610612738, 1610612747}
KNOWN_PLAYER_IDS = {201939, 2544, 1628369}


class IntegrationDatabaseError(RuntimeError):
    """Raised when integration tests are pointed at the primary database."""


def load_fixture(name: str) -> list[dict[str, Any]]:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def _season_fixture(prefix: str, season: str) -> list[dict[str, Any]]:
    return load_fixture(f"{prefix}_{season.replace('-', '_')}.json")


class FakeNbaSource:
    def __init__(
        self,
        teams: list[dict[str, Any]] | None = None,
        players: list[dict[str, Any]] | None = None,
        *,
        team_logs: dict[str, list[dict[str, Any]]] | None = None,
        player_logs: dict[str, list[dict[str, Any]]] | None = None,
        fail_teams: Exception | None = None,
        fail_players: Exception | None = None,
        fail_team_log: Exception | None = None,
        fail_player_log: Exception | None = None,
    ) -> None:
        self.teams = teams if teams is not None else load_fixture("teams.json")
        self.players = players if players is not None else load_fixture("players_valid.json")
        self.team_logs = team_logs
        self.player_logs = player_logs
        self.fail_teams = fail_teams
        self.fail_players = fail_players
        self.fail_team_log = fail_team_log
        self.fail_player_log = fail_player_log
        self.team_calls = 0
        self.player_calls = 0
        self.team_log_calls: list[str] = []
        self.player_log_calls: list[str] = []

    def fetch_teams(self) -> list[dict]:
        self.team_calls += 1
        if self.fail_teams is not None:
            raise self.fail_teams
        return self.teams

    def fetch_active_players(self, season: str) -> list[dict]:
        del season
        self.player_calls += 1
        if self.fail_players is not None:
            raise self.fail_players
        return self.players

    def fetch_team_game_log(self, season: str) -> list[dict]:
        self.team_log_calls.append(season)
        if self.fail_team_log is not None:
            raise self.fail_team_log
        if self.team_logs is not None and season in self.team_logs:
            return self.team_logs[season]
        return _season_fixture("team_game_log", season)

    def fetch_player_game_log(self, season: str) -> list[dict]:
        self.player_log_calls.append(season)
        if self.fail_player_log is not None:
            raise self.fail_player_log
        if self.player_logs is not None and season in self.player_logs:
            return self.player_logs[season]
        return _season_fixture("player_game_log", season)


class FakeImportStore:
    def __init__(self, *, fail_persist: Exception | None = None) -> None:
        self.fail_persist = fail_persist
        self.running_ids: list[int] = []
        self.completed: list[dict[str, Any]] = []
        self.failed: list[dict[str, Any]] = []
        self.teams: list[NormalizedTeam] = []
        self.players: list[NormalizedPlayer] = []
        self._next_id = 1

    def insert_running(self, import_type: str = "TEAMS_PLAYERS") -> int:
        del import_type
        run_id = self._next_id
        self._next_id += 1
        self.running_ids.append(run_id)
        return run_id

    def persist_and_complete(
        self,
        run_id: int,
        *,
        teams: list[NormalizedTeam],
        players: list[NormalizedPlayer],
        records_processed: int,
        records_failed: int,
        details: dict[str, Any],
    ) -> int:
        if self.fail_persist is not None:
            raise self.fail_persist
        self.teams = list(teams)
        self.players = list(players)
        details.setdefault("players", {})["deactivation"] = 0
        self.completed.append(
            {
                "run_id": run_id,
                "records_processed": records_processed,
                "records_failed": records_failed,
                "details": details,
            }
        )
        return 0

    def mark_failed(
        self,
        run_id: int,
        *,
        records_processed: int,
        records_failed: int,
        error_message: str | None,
        details: dict[str, Any],
    ) -> None:
        self.failed.append(
            {
                "run_id": run_id,
                "records_processed": records_processed,
                "records_failed": records_failed,
                "error_message": error_message,
                "details": details,
            }
        )


class FakeGamesStore:
    def __init__(
        self,
        *,
        team_ids: set[int] | None = None,
        player_ids: set[int] | None = None,
        fail_persist: Exception | None = None,
    ) -> None:
        self.team_ids = team_ids if team_ids is not None else set(KNOWN_TEAM_IDS)
        self.player_ids = player_ids if player_ids is not None else set(KNOWN_PLAYER_IDS)
        self.fail_persist = fail_persist
        self.running_ids: list[int] = []
        self.completed: list[dict[str, Any]] = []
        self.failed: list[dict[str, Any]] = []
        self.games: list[NormalizedGame] = []
        self.stats: list[NormalizedPlayerGameStat] = []
        self.stubs: list[HistoricalPlayerStub] = []
        self.checkpoints: dict[tuple[str, str, str], CheckpointRecord] = {}
        self._next_id = 1

    def insert_running(self, import_type: str = "GAMES_STATS") -> int:
        del import_type
        run_id = self._next_id
        self._next_id += 1
        self.running_ids.append(run_id)
        return run_id

    def list_team_ids(self) -> set[int]:
        return set(self.team_ids)

    def list_player_ids(self) -> set[int]:
        return set(self.player_ids)

    def load_checkpoint(
        self, import_type: str, season: str, resource: str
    ) -> CheckpointRecord | None:
        return self.checkpoints.get((import_type, season, resource))

    def save_fetched_checkpoint(
        self,
        *,
        import_type: str,
        season: str,
        resource: str,
        rows: list[dict[str, Any]],
    ) -> None:
        self.checkpoints[(import_type, season, resource)] = CheckpointRecord(
            import_type=import_type,
            season=season,
            resource=resource,
            status="FETCHED",
            row_count=len(rows),
            payload=rows,
        )

    def persist_games_and_complete(
        self,
        run_id: int,
        *,
        stubs: list[HistoricalPlayerStub],
        games: list[NormalizedGame],
        stats: list[NormalizedPlayerGameStat],
        seasons: tuple[str, ...],
        records_processed: int,
        records_failed: int,
        details: dict[str, Any],
    ) -> int:
        if self.fail_persist is not None:
            raise self.fail_persist
        self.stubs = list(stubs)
        self.games = list(games)
        self.stats = list(stats)
        inserted = 0
        for stub in stubs:
            if stub.nba_player_id not in self.player_ids:
                self.player_ids.add(stub.nba_player_id)
                inserted += 1
        for season in seasons:
            for resource in ("team_game_log", "player_game_log"):
                key = ("GAMES_STATS", season, resource)
                existing = self.checkpoints.get(key)
                if existing is None:
                    continue
                self.checkpoints[key] = CheckpointRecord(
                    import_type=existing.import_type,
                    season=existing.season,
                    resource=existing.resource,
                    status="PERSISTED",
                    row_count=existing.row_count,
                    payload=None,
                )
        details = {**details, "historical_players_inserted": inserted}
        self.completed.append(
            {
                "run_id": run_id,
                "records_processed": records_processed,
                "records_failed": records_failed,
                "details": details,
            }
        )
        return inserted

    def mark_failed(
        self,
        run_id: int,
        *,
        records_processed: int,
        records_failed: int,
        error_message: str | None,
        details: dict[str, Any],
    ) -> None:
        self.failed.append(
            {
                "run_id": run_id,
                "records_processed": records_processed,
                "records_failed": records_failed,
                "error_message": error_message,
                "details": details,
            }
        )


@pytest.fixture
def valid_source() -> FakeNbaSource:
    return FakeNbaSource()


@pytest.fixture
def test_config(monkeypatch: pytest.MonkeyPatch):
    from splitedge_importer.config import Config

    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv(
        "DATABASE_URL",
        "postgresql://splitedge:s3cretpass@localhost:5432/splitedge_test",
    )
    monkeypatch.setenv("NBA_IMPORT_SEASONS", "2023-24,2024-25,2025-26")
    return Config(
        database_url="postgresql://splitedge:s3cretpass@localhost:5432/splitedge_test",
        nba_season="2025-26",
        import_seasons=("2023-24", "2024-25", "2025-26"),
        min_teams=1,
        min_active_players=1,
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )


def database_name(database_url: str) -> str:
    return (urlparse(database_url).path or "").lstrip("/")


def require_integration_database(database_url: str) -> str:
    db_name = database_name(database_url)
    if db_name == "splitedge":
        raise IntegrationDatabaseError(
            "refusing to run integration tests against the primary splitedge database; "
            "use splitedge_test"
        )
    return db_name


def _maven_command() -> str | None:
    if os.name == "nt":
        return shutil.which("mvn.cmd") or shutil.which("mvn.bat") or shutil.which("mvn")
    return shutil.which("mvn")


@pytest.fixture(scope="session")
def migrated_database() -> str:
    database_url = (os.environ.get("DATABASE_URL") or "").strip()
    if not database_url:
        pytest.skip("DATABASE_URL is not set")

    try:
        require_integration_database(database_url)
    except IntegrationDatabaseError as exc:
        pytest.fail(str(exc))

    psycopg = pytest.importorskip("psycopg")
    with psycopg.connect(database_url, autocommit=True) as conn:
        conn.execute("DROP SCHEMA IF EXISTS public CASCADE")
        conn.execute("CREATE SCHEMA public")
        conn.execute("GRANT ALL ON SCHEMA public TO CURRENT_USER")
        conn.execute("GRANT ALL ON SCHEMA public TO public")

    mvn = _maven_command()
    if mvn is None:
        pytest.skip("Maven is required to apply Flyway migrations")

    env = os.environ.copy()
    user = url_user(database_url) or "splitedge"
    password = url_password(database_url) or "splitedge_local"
    completed = subprocess.run(
        [
            mvn,
            "-f",
            str(BACKEND_POM),
            "--batch-mode",
            f"-Dflyway.url={jdbc_url(database_url)}",
            f"-Dflyway.user={user}",
            f"-Dflyway.password={password}",
            "flyway:migrate",
        ],
        check=False,
        env=env,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        pytest.fail(
            "Flyway migrate failed:\n"
            f"{completed.stdout[-2000:]}\n{completed.stderr[-2000:]}"
        )
    return database_url


@pytest.fixture
def db_connection(migrated_database: str):
    psycopg = pytest.importorskip("psycopg")
    with psycopg.connect(migrated_database) as conn:
        conn.execute(
            """
            TRUNCATE import_checkpoints, player_game_stats, games, import_runs, players, teams
            RESTART IDENTITY CASCADE
            """
        )
        conn.commit()
        yield conn
        conn.rollback()
