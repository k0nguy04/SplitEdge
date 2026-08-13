from splitedge_importer.models import CheckpointRecord
from splitedge_importer.persistence.checkpoints import reusable_fetched_rows

VALID_TEAM_ROW = {
    "GAME_ID": "0022300001",
    "TEAM_ID": 1610612744,
    "TEAM_ABBREVIATION": "GSW",
    "GAME_DATE": "2023-10-24",
    "MATCHUP": "GSW vs. BOS",
    "WL": "W",
    "PTS": 122,
}


def _fetched(payload: object, **overrides: object) -> CheckpointRecord:
    values = {
        "import_type": "GAMES_STATS",
        "season": "2023-24",
        "resource": "team_game_log",
        "status": "FETCHED",
        "row_count": 1,
        "payload": payload,
    }
    values.update(overrides)
    return CheckpointRecord(**values)  # type: ignore[arg-type]


def test_reuses_fetched_list_of_dicts() -> None:
    rows = reusable_fetched_rows(
        _fetched([VALID_TEAM_ROW]),
        import_type="GAMES_STATS",
        season="2023-24",
        resource="team_game_log",
    )
    assert rows == [VALID_TEAM_ROW]


def test_reuses_headers_rowset_payload() -> None:
    payload = {
        "headers": list(VALID_TEAM_ROW.keys()),
        "rowSet": [list(VALID_TEAM_ROW.values())],
    }
    rows = reusable_fetched_rows(
        _fetched(payload),
        import_type="GAMES_STATS",
        season="2023-24",
        resource="team_game_log",
    )
    assert rows is not None
    assert rows[0]["GAME_ID"] == "0022300001"


def test_does_not_reuse_persisted() -> None:
    rows = reusable_fetched_rows(
        _fetched(None, status="PERSISTED"),
        import_type="GAMES_STATS",
        season="2023-24",
        resource="team_game_log",
    )
    assert rows is None


def test_does_not_reuse_empty_or_malformed() -> None:
    assert (
        reusable_fetched_rows(
            _fetched([]),
            import_type="GAMES_STATS",
            season="2023-24",
            resource="team_game_log",
        )
        is None
    )
    assert (
        reusable_fetched_rows(
            _fetched({"headers": ["GAME_ID"], "rowSet": []}),
            import_type="GAMES_STATS",
            season="2023-24",
            resource="team_game_log",
        )
        is None
    )
    assert (
        reusable_fetched_rows(
            _fetched([{"GAME_ID": "0022300001"}]),
            import_type="GAMES_STATS",
            season="2023-24",
            resource="team_game_log",
        )
        is None
    )


def test_does_not_reuse_wrong_season_or_resource() -> None:
    checkpoint = _fetched([VALID_TEAM_ROW])
    assert (
        reusable_fetched_rows(
            checkpoint,
            import_type="GAMES_STATS",
            season="2024-25",
            resource="team_game_log",
        )
        is None
    )
    assert (
        reusable_fetched_rows(
            checkpoint,
            import_type="GAMES_STATS",
            season="2023-24",
            resource="player_game_log",
        )
        is None
    )
    assert (
        reusable_fetched_rows(
            checkpoint,
            import_type="TEAMS_PLAYERS",
            season="2023-24",
            resource="team_game_log",
        )
        is None
    )
