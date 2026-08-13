"""Import store protocol and PostgreSQL implementation."""

from __future__ import annotations

from typing import Any, Protocol

from splitedge_importer.models import (
    CheckpointRecord,
    HistoricalPlayerStub,
    NormalizedGame,
    NormalizedPlayer,
    NormalizedPlayerGameStat,
    NormalizedTeam,
)
from splitedge_importer.persistence.checkpoints import load_checkpoint as load_checkpoint_row
from splitedge_importer.persistence.checkpoints import mark_checkpoints_persisted
from splitedge_importer.persistence.checkpoints import (
    save_fetched_checkpoint as write_fetched_checkpoint,
)
from splitedge_importer.persistence.db import connection
from splitedge_importer.persistence.games import upsert_games
from splitedge_importer.persistence.historical_players import insert_historical_stubs
from splitedge_importer.persistence.import_runs import (
    insert_running,
    update_completed,
    update_failed,
)
from splitedge_importer.persistence.player_stats import upsert_player_stats
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


class GamesImportStore(Protocol):
    def insert_running(self, import_type: str = "GAMES_STATS") -> int: ...

    def list_team_ids(self) -> set[int]: ...

    def list_player_ids(self) -> set[int]: ...

    def load_checkpoint(
        self, import_type: str, season: str, resource: str
    ) -> CheckpointRecord | None: ...

    def save_fetched_checkpoint(
        self,
        *,
        import_type: str,
        season: str,
        resource: str,
        rows: list[dict[str, Any]],
    ) -> None: ...

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

    def list_team_ids(self) -> set[int]:
        with connection(self._database_url) as conn:
            rows = conn.execute("SELECT nba_team_id FROM teams").fetchall()
            return {int(row[0]) for row in rows}

    def list_player_ids(self) -> set[int]:
        with connection(self._database_url) as conn:
            rows = conn.execute("SELECT nba_player_id FROM players").fetchall()
            return {int(row[0]) for row in rows}

    def load_checkpoint(
        self, import_type: str, season: str, resource: str
    ) -> CheckpointRecord | None:
        with connection(self._database_url) as conn:
            return load_checkpoint_row(conn, import_type, season, resource)

    def save_fetched_checkpoint(
        self,
        *,
        import_type: str,
        season: str,
        resource: str,
        rows: list[dict[str, Any]],
    ) -> None:
        with connection(self._database_url) as conn:
            write_fetched_checkpoint(
                conn,
                import_type=import_type,
                season=season,
                resource=resource,
                rows=rows,
            )
            conn.commit()

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
        with connection(self._database_url) as conn:
            with conn.transaction():
                inserted = insert_historical_stubs(conn, stubs)
                upsert_games(conn, games)
                upsert_player_stats(conn, stats)
                mark_checkpoints_persisted(
                    conn,
                    import_type="GAMES_STATS",
                    seasons=seasons,
                )
                details = {**details, "historical_players_inserted": inserted}
                update_completed(
                    conn,
                    run_id,
                    records_processed=records_processed,
                    records_failed=records_failed,
                    details=details,
                )
            return inserted
