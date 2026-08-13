import pytest
from conftest import FakeNbaSource, load_fixture

from splitedge_importer.config import Config
from splitedge_importer.games_pipeline import run_games_pipeline
from splitedge_importer.persistence.store import PostgresImportStore
from splitedge_importer.pipeline import run_pipeline

pytestmark = pytest.mark.integration


def _teams_config(database_url: str) -> Config:
    return Config(
        database_url=database_url,
        nba_season="2025-26",
        min_teams=1,
        min_active_players=1,
    )


def _games_config(database_url: str) -> Config:
    return Config(
        database_url=database_url,
        nba_season="2025-26",
        import_seasons=("2023-24", "2024-25", "2025-26"),
        min_teams=1,
        min_active_players=1,
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )


def _seed_teams_players(database_url: str) -> None:
    run_pipeline(
        _teams_config(database_url),
        source=FakeNbaSource(),
        store=PostgresImportStore(database_url),
    )


class FailOnceGamesStore(PostgresImportStore):
    def __init__(self, database_url: str) -> None:
        super().__init__(database_url)
        self.fail_once = True

    def persist_games_and_complete(self, *args, **kwargs) -> int:
        if self.fail_once:
            self.fail_once = False
            raise RuntimeError("forced TX2 failure")
        return super().persist_games_and_complete(*args, **kwargs)


def test_games_import_twice_is_idempotent(db_connection, migrated_database: str) -> None:
    _seed_teams_players(migrated_database)
    config = _games_config(migrated_database)
    store = PostgresImportStore(migrated_database)
    source = FakeNbaSource()
    first = run_games_pipeline(config, source=source, store=store)
    second = run_games_pipeline(config, source=source, store=store)
    assert first.success and second.success
    games = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats = db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0]
    assert games == 4
    assert stats == 10
    unique = db_connection.execute(
        """
        SELECT COUNT(*) FROM (
            SELECT nba_player_id, nba_game_id
            FROM player_game_stats
            GROUP BY nba_player_id, nba_game_id
        ) rows
        """
    ).fetchone()[0]
    assert unique == 10


def test_score_change_updates_existing_game(db_connection, migrated_database: str) -> None:
    _seed_teams_players(migrated_database)
    config = _games_config(migrated_database)
    store = PostgresImportStore(migrated_database)
    run_games_pipeline(config, source=FakeNbaSource(), store=store)
    first = db_connection.execute(
        "SELECT updated_at FROM games WHERE nba_game_id = '0022300001'"
    ).fetchone()[0]
    rows = load_fixture("team_game_log_2023_24.json")
    for row in rows:
        if row["GAME_ID"] == "0022300001" and row["TEAM_ID"] == 1610612744:
            row["PTS"] = 130
    result = run_games_pipeline(
        config,
        source=FakeNbaSource(team_logs={"2023-24": rows}),
        store=store,
    )
    assert result.success is True
    row = db_connection.execute(
        "SELECT home_score, updated_at FROM games WHERE nba_game_id = '0022300001'"
    ).fetchone()
    assert row[0] == 130
    assert row[1] >= first


def test_historical_stub_does_not_change_active_player(
    db_connection, migrated_database: str
) -> None:
    _seed_teams_players(migrated_database)
    before = db_connection.execute(
        """
        SELECT is_active, nba_team_id, first_name, last_name, full_name
        FROM players WHERE nba_player_id = 201939
        """
    ).fetchone()
    result = run_games_pipeline(
        _games_config(migrated_database),
        source=FakeNbaSource(),
        store=PostgresImportStore(migrated_database),
    )
    assert result.success is True
    stub = db_connection.execute(
        """
        SELECT is_active, nba_team_id, first_name, last_name, full_name
        FROM players WHERE nba_player_id = 777777
        """
    ).fetchone()
    assert stub[0] is False
    assert stub[1] is None
    assert stub[2] == "Marcus Historical"
    assert stub[3] == "Marcus Historical"
    assert stub[4] == "Marcus Historical"
    after = db_connection.execute(
        """
        SELECT is_active, nba_team_id, first_name, last_name, full_name
        FROM players WHERE nba_player_id = 201939
        """
    ).fetchone()
    assert after == before
    assert after[0] is True
    assert after[1] == 1610612744


def test_forced_tx2_failure_then_checkpoint_retry(
    db_connection, migrated_database: str
) -> None:
    _seed_teams_players(migrated_database)
    config = _games_config(migrated_database)
    store = FailOnceGamesStore(migrated_database)
    first = run_games_pipeline(config, source=FakeNbaSource(), store=store)
    assert first.success is False
    assert first.status == "FAILED"
    games_after_fail = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats_after_fail = db_connection.execute(
        "SELECT COUNT(*) FROM player_game_stats"
    ).fetchone()[0]
    assert games_after_fail == 0
    assert stats_after_fail == 0
    fetched = db_connection.execute(
        """
        SELECT status, payload IS NOT NULL
        FROM import_checkpoints
        WHERE import_type = 'GAMES_STATS'
        """
    ).fetchall()
    assert fetched
    assert all(row[0] == "FETCHED" and row[1] is True for row in fetched)

    raising = FakeNbaSource(
        fail_team_log=RuntimeError("HTTP should not be called"),
        fail_player_log=RuntimeError("HTTP should not be called"),
    )
    second = run_games_pipeline(config, source=raising, store=store)
    assert second.success is True
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
    games = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats = db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0]
    assert games == 4
    assert stats == 10
    persisted = db_connection.execute(
        """
        SELECT status, payload
        FROM import_checkpoints
        WHERE import_type = 'GAMES_STATS'
        """
    ).fetchall()
    assert all(row[0] == "PERSISTED" and row[1] is None for row in persisted)

    third = run_games_pipeline(config, source=raising, store=store)
    assert third.success is False
    assert "HTTP should not be called" in (third.error_message or "")


def test_schema_has_games_tables_without_combination_columns(db_connection) -> None:
    tables = {
        row[0]
        for row in db_connection.execute(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
            """
        )
    }
    assert "games" in tables
    assert "player_game_stats" in tables
    assert "import_checkpoints" in tables
    columns = {
        row[0]
        for row in db_connection.execute(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'player_game_stats'
            """
        )
    }
    assert "minutes" in columns
    assert "points" in columns
    assert "rebounds" in columns
    assert "assists" in columns
    assert "three_pointers_made" in columns
    assert "pr" not in columns
    assert "pa" not in columns
    assert "ra" not in columns
    assert "pra" not in columns
