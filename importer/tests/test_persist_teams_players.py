import pytest
from conftest import FakeNbaSource, load_fixture

from splitedge_importer.config import Config
from splitedge_importer.models import NormalizedPlayer, NormalizedTeam
from splitedge_importer.persistence.players import deactivate_missing_players
from splitedge_importer.persistence.store import PostgresImportStore
from splitedge_importer.pipeline import run_pipeline

pytestmark = pytest.mark.integration


def _config(database_url: str) -> Config:
    return Config(
        database_url=database_url,
        nba_season="2025-26",
        min_teams=1,
        min_active_players=1,
    )


def _counts(conn) -> tuple[int, int]:
    teams = conn.execute("SELECT COUNT(*) FROM teams").fetchone()[0]
    players = conn.execute("SELECT COUNT(*) FROM players").fetchone()[0]
    return int(teams), int(players)


def test_import_twice_is_idempotent(db_connection, migrated_database: str) -> None:
    config = _config(migrated_database)
    source = FakeNbaSource()
    store = PostgresImportStore(migrated_database)
    first = run_pipeline(config, source=source, store=store)
    second = run_pipeline(config, source=source, store=store)
    assert first.success and second.success
    teams, players = _counts(db_connection)
    assert teams == 3
    assert players == 3
    ids = [
        row[0]
        for row in db_connection.execute("SELECT nba_player_id FROM players ORDER BY nba_player_id")
    ]
    assert ids == [2544, 201939, 1628369]


def test_name_change_updates_existing_row(db_connection, migrated_database: str) -> None:
    config = _config(migrated_database)
    store = PostgresImportStore(migrated_database)
    run_pipeline(config, source=FakeNbaSource(), store=store)
    first = db_connection.execute(
        "SELECT updated_at FROM teams WHERE nba_team_id = 1610612744"
    ).fetchone()[0]
    updated_teams = load_fixture("teams.json")
    updated_teams[0]["full_name"] = "Golden State Warriors Updated"
    result = run_pipeline(
        config,
        source=FakeNbaSource(teams=updated_teams),
        store=store,
    )
    assert result.success is True
    row = db_connection.execute(
        "SELECT full_name, updated_at FROM teams WHERE nba_team_id = 1610612744"
    ).fetchone()
    assert row[0] == "Golden State Warriors Updated"
    assert row[1] >= first


def test_omitted_player_is_deactivated_when_batch_is_valid(
    db_connection, migrated_database: str
) -> None:
    config = _config(migrated_database)
    store = PostgresImportStore(migrated_database)
    run_pipeline(config, source=FakeNbaSource(), store=store)
    reduced = [row for row in load_fixture("players_valid.json") if row["PERSON_ID"] != 1628369]
    result = run_pipeline(
        config,
        source=FakeNbaSource(players=reduced),
        store=store,
    )
    assert result.success is True
    assert result.details["players"]["deactivation"] == 1
    active = db_connection.execute(
        "SELECT nba_player_id, is_active FROM players WHERE nba_player_id = 1628369"
    ).fetchone()
    assert active[1] is False
    remaining = db_connection.execute("SELECT COUNT(*) FROM players").fetchone()[0]
    assert remaining == 3


def test_empty_second_batch_does_not_deactivate_or_commit(
    db_connection, migrated_database: str
) -> None:
    config = _config(migrated_database)
    store = PostgresImportStore(migrated_database)
    run_pipeline(config, source=FakeNbaSource(), store=store)
    result = run_pipeline(config, source=FakeNbaSource(players=[]), store=store)
    assert result.success is False
    assert result.details["guard"]["reason"] == "players_empty"
    active = db_connection.execute("SELECT COUNT(*) FROM players WHERE is_active").fetchone()[0]
    assert active == 3
    failed_status = db_connection.execute(
        "SELECT status, records_processed FROM import_runs ORDER BY id DESC LIMIT 1"
    ).fetchone()
    assert failed_status[0] == "FAILED"
    assert failed_status[1] == 0


def test_tx2_failure_rolls_back_data_and_marks_failed(
    db_connection, migrated_database: str, monkeypatch
) -> None:
    config = _config(migrated_database)
    store = PostgresImportStore(migrated_database)
    run_pipeline(config, source=FakeNbaSource(), store=store)

    def boom(_conn, _players: list[NormalizedPlayer]) -> int:
        raise RuntimeError("completed update failed")

    monkeypatch.setattr(
        "splitedge_importer.persistence.store.deactivate_missing_players",
        boom,
    )
    result = run_pipeline(
        config,
        source=FakeNbaSource(
            players=[
                row
                for row in load_fixture("players_valid.json")
                if row["PERSON_ID"] != 1628369
            ]
            + [
                {
                    "PERSON_ID": 203076,
                    "DISPLAY_FIRST_LAST": "Anthony Davis",
                    "ROSTERSTATUS": 1,
                    "TEAM_ID": 1610612747,
                }
            ]
        ),
        store=store,
    )
    assert result.success is False
    names = {
        row[0]
        for row in db_connection.execute("SELECT full_name FROM players")
    }
    assert "Anthony Davis" not in names
    assert "Jayson Tatum" in names
    status = db_connection.execute(
        "SELECT status FROM import_runs ORDER BY id DESC LIMIT 1"
    ).fetchone()[0]
    assert status == "FAILED"


def test_refuses_deactivation_with_empty_id_set(db_connection) -> None:
    with pytest.raises(RuntimeError, match="empty"):
        deactivate_missing_players(db_connection, [])


def test_warned_player_is_persisted(db_connection, migrated_database: str) -> None:
    config = _config(migrated_database)
    players = load_fixture("players_valid.json") + [
        {
            "PERSON_ID": 1629029,
            "DISPLAY_FIRST_LAST": "Luka Doncic",
            "ROSTERSTATUS": 1,
            "TEAM_ID": 0,
        }
    ]
    result = run_pipeline(
        config,
        source=FakeNbaSource(players=players),
        store=PostgresImportStore(migrated_database),
    )
    assert result.success is True
    assert result.details["players"]["warning"] == 1
    assert result.records_failed == 0
    row = db_connection.execute(
        "SELECT nba_team_id, is_active FROM players WHERE nba_player_id = 1629029"
    ).fetchone()
    assert row[0] is None
    assert row[1] is True


def test_completed_update_is_in_same_transaction_as_upserts(
    db_connection, migrated_database: str
) -> None:
    result = run_pipeline(
        _config(migrated_database),
        source=FakeNbaSource(),
        store=PostgresImportStore(migrated_database),
    )
    run = db_connection.execute(
        """
        SELECT status, records_processed, records_failed, completed_at IS NOT NULL, details
        FROM import_runs
        WHERE id = %s
        """,
        (result.run_id,),
    ).fetchone()
    assert run[0] == "COMPLETED"
    assert run[1] == 6
    assert run[2] == 0
    assert run[3] is True
    assert run[4]["teams"]["persisted"] == 3
    assert run[4]["players"]["persisted"] == 3
    assert "deactivation" in run[4]["teams"]
    assert "deactivation" in run[4]["players"]


def test_schema_has_no_games_tables(db_connection) -> None:
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
    assert "teams" in tables
    assert "players" in tables
    assert "import_runs" in tables
    assert "games" not in tables
    assert "player_game_stats" not in tables


def test_normalized_models_round_trip(db_connection, migrated_database: str) -> None:
    store = PostgresImportStore(migrated_database)
    run_id = store.insert_running()
    deactivated = store.persist_and_complete(
        run_id,
        teams=[
            NormalizedTeam(
                nba_team_id=1610612744,
                abbreviation="GSW",
                full_name="Golden State Warriors",
                nickname="Warriors",
                city="Golden State",
            )
        ],
        players=[
            NormalizedPlayer(
                nba_player_id=201939,
                first_name="Stephen",
                last_name="Curry",
                full_name="Stephen Curry",
                is_active=True,
                nba_team_id=1610612744,
            )
        ],
        records_processed=2,
        records_failed=0,
        details={
            "teams": {
                "received": 1,
                "persisted": 1,
                "rejected": 0,
                "warning": 0,
                "deactivation": 0,
            },
            "players": {
                "received": 1,
                "persisted": 1,
                "rejected": 0,
                "warning": 0,
                "deactivation": 0,
            },
        },
    )
    assert deactivated == 0
    team_id = db_connection.execute(
        "SELECT nba_team_id FROM teams WHERE nba_team_id = 1610612744"
    ).fetchone()[0]
    assert team_id == 1610612744
