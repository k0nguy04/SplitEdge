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
        fail_summary=RuntimeError("HTTP should not be called"),
    )
    second = run_games_pipeline(config, source=raising, store=store)
    assert second.success is True
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
    assert raising.summary_calls == []
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


def _neutral_games_config(database_url: str) -> Config:
    return Config(
        database_url=database_url,
        nba_season="2025-26",
        import_seasons=("2024-25",),
        min_teams=1,
        min_active_players=1,
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )


def test_resolver_failure_preserves_prior_games_and_stats(
    db_connection, migrated_database: str
) -> None:
    _seed_teams_players(migrated_database)
    store = PostgresImportStore(migrated_database)
    first = run_games_pipeline(
        _games_config(migrated_database),
        source=FakeNbaSource(),
        store=store,
    )
    assert first.success is True
    games_before = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats_before = db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0]
    assert games_before == 4
    assert stats_before == 10

    failed = run_games_pipeline(
        _neutral_games_config(migrated_database),
        source=FakeNbaSource(
            team_logs={"2024-25": load_fixture("team_game_log_neutral.json")},
            player_logs={"2024-25": load_fixture("player_game_log_neutral.json")},
            summaries={"0022400999": {"gameStatus": 3, "gameStatusText": "In Progress"}},
        ),
        store=store,
    )
    assert failed.success is False
    assert failed.status == "FAILED"
    games_after = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats_after = db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0]
    assert games_after == games_before
    assert stats_after == stats_before
    assert (
        db_connection.execute(
            "SELECT COUNT(*) FROM games WHERE nba_game_id = '0022400999'"
        ).fetchone()[0]
        == 0
    )


def test_fetched_summary_checkpoint_remains_after_resolver_failure(
    db_connection, migrated_database: str
) -> None:
    _seed_teams_players(migrated_database)
    store = PostgresImportStore(migrated_database)
    second_rows = [
        {**row, "GAME_ID": "0022400998"}
        for row in load_fixture("team_game_log_neutral.json")
    ]
    source = FakeNbaSource(
        team_logs={"2024-25": load_fixture("team_game_log_neutral.json") + second_rows},
        player_logs={"2024-25": load_fixture("player_game_log_neutral.json")},
        summaries={
            "0022400999": dict(load_fixture("box_score_summary_neutral.json")),
            "0022400998": {
                **dict(load_fixture("box_score_summary_neutral.json")),
                "gameId": "0022400998",
                "gameStatus": 2,
            },
        },
    )
    result = run_games_pipeline(
        _neutral_games_config(migrated_database),
        source=source,
        store=store,
    )
    assert result.success is False
    row = db_connection.execute(
        """
        SELECT status, payload
        FROM import_checkpoints
        WHERE import_type = 'GAMES_STATS'
          AND season = '2024-25'
          AND resource = 'box_score_summary:0022400999'
        """
    ).fetchone()
    assert row is not None
    assert row[0] == "FETCHED"
    assert row[1]["gameId"] == "0022400999"
    missing = db_connection.execute(
        """
        SELECT COUNT(*)
        FROM import_checkpoints
        WHERE resource = 'box_score_summary:0022400998'
        """
    ).fetchone()[0]
    assert missing == 0
    assert db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0] == 0


def test_neutral_site_import_is_idempotent(db_connection, migrated_database: str) -> None:
    _seed_teams_players(migrated_database)
    config = _games_config(migrated_database)
    store = PostgresImportStore(migrated_database)
    source = FakeNbaSource(
        team_logs={
            "2023-24": load_fixture("team_game_log_2023_24.json"),
            "2024-25": load_fixture("team_game_log_2024_25.json")
            + load_fixture("team_game_log_neutral.json"),
            "2025-26": load_fixture("team_game_log_2025_26.json"),
        },
        player_logs={
            "2023-24": load_fixture("player_game_log_2023_24.json"),
            "2024-25": load_fixture("player_game_log_2024_25.json")
            + load_fixture("player_game_log_neutral.json"),
            "2025-26": load_fixture("player_game_log_2025_26.json"),
        },
        summaries={"0022400999": dict(load_fixture("box_score_summary_neutral.json"))},
    )
    first = run_games_pipeline(config, source=source, store=store)
    second = run_games_pipeline(config, source=source, store=store)
    assert first.success and second.success
    games = db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0]
    stats = db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0]
    assert games == 5
    assert stats == 12
    row = db_connection.execute(
        """
        SELECT home_nba_team_id, away_nba_team_id, home_score, away_score
        FROM games WHERE nba_game_id = '0022400999'
        """
    ).fetchone()
    assert row == (1610612744, 1610612738, 118, 110)


def test_tx2_marks_only_used_checkpoints_leaving_stale_summary(
    db_connection, migrated_database: str
) -> None:
    from psycopg.types.json import Jsonb

    _seed_teams_players(migrated_database)
    db_connection.execute(
        """
        INSERT INTO import_checkpoints (
            import_type, season, resource, status, row_count, payload, updated_at
        )
        VALUES (
            'GAMES_STATS', '2024-25', 'box_score_summary:0022499999',
            'FETCHED', 1, %s, NOW()
        )
        """,
        (Jsonb({"gameId": "0022499999", "unused": True}),),
    )
    db_connection.commit()
    result = run_games_pipeline(
        _games_config(migrated_database),
        source=FakeNbaSource(),
        store=PostgresImportStore(migrated_database),
    )
    assert result.success is True
    stale = db_connection.execute(
        """
        SELECT status, payload
        FROM import_checkpoints
        WHERE resource = 'box_score_summary:0022499999'
        """
    ).fetchone()
    assert stale[0] == "FETCHED"
    assert stale[1]["unused"] is True
    used = db_connection.execute(
        """
        SELECT resource, status, payload
        FROM import_checkpoints
        WHERE import_type = 'GAMES_STATS'
          AND resource IN ('team_game_log', 'player_game_log')
        """
    ).fetchall()
    assert used
    assert all(row[1] == "PERSISTED" and row[2] is None for row in used)
    assert (
        db_connection.execute(
            """
            SELECT COUNT(*) FROM import_checkpoints
            WHERE resource LIKE 'box_score_summary:0022400%'
            """
        ).fetchone()[0]
        == 0
    )


def test_neutral_forced_tx2_retry_makes_zero_http_calls(
    db_connection, migrated_database: str
) -> None:
    _seed_teams_players(migrated_database)
    config = _neutral_games_config(migrated_database)
    store = FailOnceGamesStore(migrated_database)
    first = run_games_pipeline(
        config,
        source=FakeNbaSource(
            team_logs={"2024-25": load_fixture("team_game_log_neutral.json")},
            player_logs={"2024-25": load_fixture("player_game_log_neutral.json")},
            summaries={"0022400999": dict(load_fixture("box_score_summary_neutral.json"))},
        ),
        store=store,
    )
    assert first.success is False
    fetched = db_connection.execute(
        """
        SELECT resource, status, payload IS NOT NULL
        FROM import_checkpoints
        WHERE import_type = 'GAMES_STATS'
        """
    ).fetchall()
    resources = {row[0]: (row[1], row[2]) for row in fetched}
    assert resources["team_game_log"] == ("FETCHED", True)
    assert resources["player_game_log"] == ("FETCHED", True)
    assert resources["box_score_summary:0022400999"] == ("FETCHED", True)

    raising = FakeNbaSource(
        fail_team_log=RuntimeError("HTTP should not be called"),
        fail_player_log=RuntimeError("HTTP should not be called"),
        fail_summary=RuntimeError("HTTP should not be called"),
    )
    second = run_games_pipeline(config, source=raising, store=store)
    assert second.success is True
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
    assert raising.summary_calls == []
    assert db_connection.execute("SELECT COUNT(*) FROM games").fetchone()[0] == 1
    assert db_connection.execute("SELECT COUNT(*) FROM player_game_stats").fetchone()[0] == 2

