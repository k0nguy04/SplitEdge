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

from splitedge_importer.models import NormalizedPlayer, NormalizedTeam
from splitedge_importer.persistence.db import jdbc_url, url_password, url_user

FIXTURES = Path(__file__).resolve().parent / "fixtures"
REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_POM = REPO_ROOT / "backend" / "pom.xml"


def load_fixture(name: str) -> list[dict[str, Any]]:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


class FakeNbaSource:
    def __init__(
        self,
        teams: list[dict[str, Any]] | None = None,
        players: list[dict[str, Any]] | None = None,
        *,
        fail_teams: Exception | None = None,
        fail_players: Exception | None = None,
    ) -> None:
        self.teams = teams if teams is not None else load_fixture("teams.json")
        self.players = players if players is not None else load_fixture("players_valid.json")
        self.fail_teams = fail_teams
        self.fail_players = fail_players
        self.team_calls = 0
        self.player_calls = 0

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
    return Config(
        database_url="postgresql://splitedge:s3cretpass@localhost:5432/splitedge_test",
        nba_season="2025-26",
        min_teams=1,
        min_active_players=1,
    )


def _database_name(database_url: str) -> str:
    return (urlparse(database_url).path or "").lstrip("/")


def _maven_command() -> str | None:
    if os.name == "nt":
        return shutil.which("mvn.cmd") or shutil.which("mvn.bat") or shutil.which("mvn")
    return shutil.which("mvn")


@pytest.fixture(scope="session")
def migrated_database() -> str:
    database_url = (os.environ.get("DATABASE_URL") or "").strip()
    if not database_url:
        pytest.skip("DATABASE_URL is not set")

    db_name = _database_name(database_url)
    if db_name == "splitedge" and os.environ.get("SPLITEDGE_ALLOW_PRIMARY_DB") != "1":
        pytest.skip("refusing to drop the primary splitedge database; use splitedge_test")

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
        conn.execute("TRUNCATE import_runs, players, teams RESTART IDENTITY CASCADE")
        conn.commit()
        yield conn
        conn.rollback()
