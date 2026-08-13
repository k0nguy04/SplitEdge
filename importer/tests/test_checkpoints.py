from splitedge_importer.models import CheckpointRecord
from splitedge_importer.persistence.checkpoints import (
    reusable_fetched_rows,
    reusable_fetched_summary,
)
from splitedge_importer.retrieval.box_score_summary import HomeAwayResolverError, summary_resource

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


VALID_SUMMARY = {
    "gameId": "0022400999",
    "gameStatus": 3,
    "gameStatusText": "Final",
    "homeTeamId": 1610612744,
    "awayTeamId": 1610612738,
}


def _fetched_summary(payload: object, **overrides: object) -> CheckpointRecord:
    values = {
        "import_type": "GAMES_STATS",
        "season": "2024-25",
        "resource": summary_resource("0022400999"),
        "status": "FETCHED",
        "row_count": 1,
        "payload": payload,
    }
    values.update(overrides)
    return CheckpointRecord(**values)  # type: ignore[arg-type]


def test_reuses_fetched_summary_dict() -> None:
    summary = reusable_fetched_summary(
        _fetched_summary(VALID_SUMMARY),
        import_type="GAMES_STATS",
        season="2024-25",
        resource=summary_resource("0022400999"),
        requested_game_id="0022400999",
        log_team_ids={1610612744, 1610612738},
    )
    assert summary is not None
    assert summary["homeTeamId"] == 1610612744
    assert summary["awayTeamId"] == 1610612738


def test_does_not_reuse_empty_or_malformed_summary() -> None:
    kwargs = {
        "import_type": "GAMES_STATS",
        "season": "2024-25",
        "resource": summary_resource("0022400999"),
        "requested_game_id": "0022400999",
        "log_team_ids": {1610612744, 1610612738},
    }
    assert reusable_fetched_summary(_fetched_summary({}), **kwargs) is None
    assert reusable_fetched_summary(_fetched_summary({"foo": 1}), **kwargs) is None
    assert reusable_fetched_summary(_fetched_summary(None, status="PERSISTED"), **kwargs) is None


def test_wrong_game_id_summary_is_not_reused() -> None:
    payload = {**VALID_SUMMARY, "gameId": "0022400001"}
    summary = reusable_fetched_summary(
        _fetched_summary(payload),
        import_type="GAMES_STATS",
        season="2024-25",
        resource=summary_resource("0022400999"),
        requested_game_id="0022400999",
        log_team_ids={1610612744, 1610612738},
    )
    assert summary is None


def test_non_final_fetched_summary_fails_the_run() -> None:
    payload = {**VALID_SUMMARY, "gameStatusText": "In Progress"}
    try:
        reusable_fetched_summary(
            _fetched_summary(payload),
            import_type="GAMES_STATS",
            season="2024-25",
            resource=summary_resource("0022400999"),
            requested_game_id="0022400999",
            log_team_ids={1610612744, 1610612738},
        )
    except HomeAwayResolverError as exc:
        assert exc.reason == "non_final_status"
    else:
        raise AssertionError("expected HomeAwayResolverError")


def test_team_mismatch_fetched_summary_fails_the_run() -> None:
    payload = {**VALID_SUMMARY, "awayTeamId": 1610612747}
    try:
        reusable_fetched_summary(
            _fetched_summary(payload),
            import_type="GAMES_STATS",
            season="2024-25",
            resource=summary_resource("0022400999"),
            requested_game_id="0022400999",
            log_team_ids={1610612744, 1610612738},
        )
    except HomeAwayResolverError as exc:
        assert exc.reason == "team_mismatch"
    else:
        raise AssertionError("expected HomeAwayResolverError")


def test_reusable_fetched_rows_ignores_summary_resource() -> None:
    rows = reusable_fetched_rows(
        _fetched_summary(VALID_SUMMARY),
        import_type="GAMES_STATS",
        season="2024-25",
        resource=summary_resource("0022400999"),
    )
    assert rows is None
