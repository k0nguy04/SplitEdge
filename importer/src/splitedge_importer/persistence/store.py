"""Import store protocol and PostgreSQL implementation."""

from __future__ import annotations

from typing import Any, Protocol

from splitedge_importer.models import NormalizedPlayer, NormalizedTeam
from splitedge_importer.persistence.db import connection
from splitedge_importer.persistence.import_runs import (
    insert_running,
    update_completed,
    update_failed,
)
from splitedge_importer.persistence.players import deactivate_missing_players, upsert_players
from splitedge_importer.persistence.teams import upsert_teams


class ImportStore(Protocol):
    def insert_running(self, import_type: str = "TEAMS_PLAYERS") -> int: ...

    def persist_and_complete(
        self,
        run_id: int,
        *,
        teams: list[NormalizedTeam],
        players: list[NormalizedPlayer],
        records_processed: int,
        records_failed: int,
        details: dict[str, Any],
    ) -> int: ...

    def mark_failed(
        self,
        run_id: int,
        *,
        records_processed: int,
        records_failed: int,
        error_message: str | None,
        details: dict[str, Any],
    ) -> None: ...


class PostgresImportStore:
    def __init__(self, database_url: str) -> None:
        self._database_url = database_url

    def insert_running(self, import_type: str = "TEAMS_PLAYERS") -> int:
        with connection(self._database_url) as conn:
            run_id = insert_running(conn, import_type)
            conn.commit()
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
        with connection(self._database_url) as conn:
            with conn.transaction():
                upsert_teams(conn, teams)
                upsert_players(conn, players)
                deactivated = deactivate_missing_players(
                    conn,
                    [player.nba_player_id for player in players],
                )
                details = {
                    **details,
                    "players": {
                        **details.get("players", {}),
                        "deactivation": deactivated,
                    },
                }
                update_completed(
                    conn,
                    run_id,
                    records_processed=records_processed,
                    records_failed=records_failed,
                    details=details,
                )
            return deactivated

    def mark_failed(
        self,
        run_id: int,
        *,
        records_processed: int,
        records_failed: int,
        error_message: str | None,
        details: dict[str, Any],
    ) -> None:
        with connection(self._database_url) as conn:
            update_failed(
                conn,
                run_id,
                records_processed=records_processed,
                records_failed=records_failed,
                error_message=error_message,
                details=details,
            )
            conn.commit()
