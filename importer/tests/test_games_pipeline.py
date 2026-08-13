import sys
from dataclasses import replace

import pytest
from conftest import FakeGamesStore, FakeNbaSource

from splitedge_importer.games_pipeline import run_games_pipeline


def test_happy_path_persists_games_and_stats(test_config) -> None:
    store = FakeGamesStore()
    source = FakeNbaSource()
    result = run_games_pipeline(test_config, source=source, store=store)
    assert result.success is True
    assert result.status == "COMPLETED"
    assert len(store.games) == 4
    assert len(store.stats) == 10
    assert result.records_processed == 14
    assert result.records_failed >= 1
    assert result.details["skipped_dnp"] == 1
    assert result.details["historical_players_inserted"] == 1
    assert store.stubs[0].nba_player_id == 777777
    assert {item.status for item in store.checkpoints.values()} == {"PERSISTED"}
    assert source.summary_calls == []


def test_running_is_inserted_before_retrieve(test_config) -> None:
    store = FakeGamesStore()
    seen: dict[str, list[int]] = {}

    class OrderedSource(FakeNbaSource):
        def fetch_team_game_log(self, season: str) -> list[dict]:
            seen.setdefault("running", list(store.running_ids))
            return super().fetch_team_game_log(season)

    result = run_games_pipeline(test_config, source=OrderedSource(), store=store)
    assert result.success is True
    assert seen["running"] == [1]


def test_missing_teams_does_not_call_nba(test_config) -> None:
    store = FakeGamesStore(team_ids=set())
    source = FakeNbaSource()
    result = run_games_pipeline(test_config, source=source, store=store)
    assert result.success is False
    assert result.details["guard"]["reason"] == "teams_below_minimum"
    assert source.team_log_calls == []
    assert store.completed == []


def test_guard_failure_keeps_fetched_checkpoints(test_config) -> None:
    store = FakeGamesStore()
    config = replace(test_config, min_games_per_season=50)
    result = run_games_pipeline(config, source=FakeNbaSource(), store=store)
    assert result.success is False
    assert result.details["guard"]["reason"] == "games_below_minimum"
    assert store.completed == []
    assert {item.status for item in store.checkpoints.values()} == {"FETCHED"}
    assert all(item.payload for item in store.checkpoints.values())


def test_tx2_failure_keeps_fetched_payloads(test_config) -> None:
    store = FakeGamesStore(fail_persist=RuntimeError("persist failed"))
    result = run_games_pipeline(test_config, source=FakeNbaSource(), store=store)
    assert result.success is False
    assert result.status == "FAILED"
    assert store.games == []
    assert {item.status for item in store.checkpoints.values()} == {"FETCHED"}


def test_retry_uses_fetched_payloads_without_http(test_config) -> None:
    store = FakeGamesStore(fail_persist=RuntimeError("forced TX2 failure"))
    first = run_games_pipeline(test_config, source=FakeNbaSource(), store=store)
    assert first.success is False
    assert {item.status for item in store.checkpoints.values()} == {"FETCHED"}

    store.fail_persist = None
    raising = FakeNbaSource(
        fail_team_log=RuntimeError("HTTP should not be called"),
        fail_player_log=RuntimeError("HTTP should not be called"),
        fail_summary=RuntimeError("HTTP should not be called"),
    )
    second = run_games_pipeline(test_config, source=raising, store=store)
    assert second.success is True
    assert second.status == "COMPLETED"
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
    assert raising.summary_calls == []
    assert {item.status for item in store.checkpoints.values()} == {"PERSISTED"}
    assert all(item.payload is None for item in store.checkpoints.values())

    third = run_games_pipeline(test_config, source=raising, store=store)
    assert third.success is False
    assert "HTTP should not be called" in (third.error_message or "")


def test_pipeline_does_not_import_nba_api(test_config) -> None:
    sys.modules.pop("nba_api", None)
    sys.modules.pop("splitedge_importer.retrieval.nba_api_client", None)
    run_games_pipeline(test_config, source=FakeNbaSource(), store=FakeGamesStore())
    assert "nba_api" not in sys.modules
    assert "splitedge_importer.retrieval.nba_api_client" not in sys.modules


def test_details_include_required_counts(test_config) -> None:
    result = run_games_pipeline(
        test_config, source=FakeNbaSource(), store=FakeGamesStore()
    )
    for entity in ("games", "player_stats"):
        for key in ("received", "persisted", "rejected", "warning", "deactivation"):
            assert key in result.details[entity]
    assert "historical_players_inserted" in result.details
    assert "skipped_dnp" in result.details
    assert "checkpoints_reused" in result.details
    assert "by_season" in result.details
    assert all("season" in item and "nba_game_id" in item for item in result.details["rejected"])


NEUTRAL_GAME_ID = "0022400999"
SECOND_AMBIGUOUS_ID = "0022400998"
STALE_SUMMARY_ID = "0022499999"
GSW = 1610612744
BOS = 1610612738


def _neutral_config(test_config):
    return replace(test_config, import_seasons=("2024-25",))


def _valid_summary(game_id: str = NEUTRAL_GAME_ID) -> dict:
    from conftest import load_fixture

    payload = dict(load_fixture("box_score_summary_neutral.json"))
    payload["gameId"] = game_id
    return payload


def _both_vs_rows(game_id: str) -> list[dict]:
    return [
        {
            "GAME_ID": game_id,
            "TEAM_ID": BOS,
            "TEAM_ABBREVIATION": "BOS",
            "GAME_DATE": "2024-11-02",
            "MATCHUP": "BOS vs. GSW",
            "WL": "L",
            "PTS": 110,
        },
        {
            "GAME_ID": game_id,
            "TEAM_ID": GSW,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2024-11-02",
            "MATCHUP": "GSW vs. BOS",
            "WL": "W",
            "PTS": 118,
        },
    ]


def _neutral_source(**kwargs):
    from conftest import load_fixture

    team_rows = load_fixture("team_game_log_2024_25.json") + load_fixture(
        "team_game_log_neutral.json"
    )
    player_rows = load_fixture("player_game_log_2024_25.json") + load_fixture(
        "player_game_log_neutral.json"
    )
    summaries = kwargs.pop("summaries", {NEUTRAL_GAME_ID: _valid_summary()})
    return FakeNbaSource(
        team_logs={"2024-25": team_rows},
        player_logs={"2024-25": player_rows},
        summaries=summaries,
        **kwargs,
    )


def _ambiguous_only_source(**kwargs):
    from conftest import load_fixture

    summaries = kwargs.pop("summaries", {NEUTRAL_GAME_ID: _valid_summary()})
    return FakeNbaSource(
        team_logs={"2024-25": load_fixture("team_game_log_neutral.json")},
        player_logs={"2024-25": load_fixture("player_game_log_neutral.json")},
        summaries=summaries,
        **kwargs,
    )


def test_ordinary_games_do_not_request_box_score_summary(test_config) -> None:
    source = FakeNbaSource()
    run_games_pipeline(test_config, source=source, store=FakeGamesStore())
    assert source.summary_calls == []


def test_valid_neutral_site_path_adds_game_and_player_rows_idempotently(test_config) -> None:
    config = _neutral_config(test_config)
    store = FakeGamesStore()
    source = _neutral_source()
    first = run_games_pipeline(config, source=source, store=store)
    assert first.success is True
    assert source.summary_calls == [NEUTRAL_GAME_ID]
    game = next(item for item in store.games if item.nba_game_id == NEUTRAL_GAME_ID)
    assert game.home_nba_team_id == GSW
    assert game.away_nba_team_id == BOS
    assert game.home_score == 118
    assert game.away_score == 110
    assert len(store.games) == 2
    assert len([item for item in store.stats if item.nba_game_id == NEUTRAL_GAME_ID]) == 2

    second_source = _neutral_source()
    second = run_games_pipeline(config, source=second_source, store=store)
    assert second.success is True
    assert len(store.games) == 2
    assert len([item for item in store.stats if item.nba_game_id == NEUTRAL_GAME_ID]) == 2


@pytest.mark.parametrize(
    ("kwargs", "reason"),
    [
        ({"fail_summary": RuntimeError("timeout")}, "network_failure"),
        ({"summaries": {NEUTRAL_GAME_ID: {}}}, "empty_summary"),
        ({"summaries": {NEUTRAL_GAME_ID: {"foo": 1}}}, "malformed_summary"),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "gameId": "0022400001"}}},
            "wrong_game_id",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "gameStatus": 2}}},
            "non_final_status",
        ),
        (
            {
                "summaries": {
                    NEUTRAL_GAME_ID: {**_valid_summary(), "gameStatusText": "In Progress"}
                }
            },
            "non_final_status",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "gameStatusText": ""}}},
            "malformed_summary",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "homeTeamId": None}}},
            "malformed_summary",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "homeTeamId": 0}}},
            "invalid_team_ids",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "awayTeamId": -8}}},
            "invalid_team_ids",
        ),
        (
            {
                "summaries": {
                    NEUTRAL_GAME_ID: {**_valid_summary(), "homeTeamId": GSW, "awayTeamId": GSW}
                }
            },
            "invalid_team_ids",
        ),
        (
            {"summaries": {NEUTRAL_GAME_ID: {**_valid_summary(), "awayTeamId": 1610612747}}},
            "team_mismatch",
        ),
    ],
)
def test_resolver_failure_fails_full_run_before_tx2(test_config, kwargs, reason) -> None:
    store = FakeGamesStore()
    result = run_games_pipeline(
        _neutral_config(test_config),
        source=_ambiguous_only_source(**kwargs),
        store=store,
    )
    assert result.success is False
    assert result.status == "FAILED"
    assert store.completed == []
    assert store.games == []
    assert result.details["resolver"]["reason"] == reason
    summary_key = ("GAMES_STATS", "2024-25", f"box_score_summary:{NEUTRAL_GAME_ID}")
    assert (
        summary_key not in store.checkpoints
        or store.checkpoints[summary_key].status == "FETCHED"
    )
    log_key = ("GAMES_STATS", "2024-25", "team_game_log")
    assert store.checkpoints[log_key].status == "FETCHED"
    assert store.checkpoints[log_key].payload


def test_fetched_summary_remains_after_later_resolver_failure(test_config) -> None:
    from conftest import load_fixture

    store = FakeGamesStore()
    team_rows = _both_vs_rows(NEUTRAL_GAME_ID) + _both_vs_rows(SECOND_AMBIGUOUS_ID)
    source = FakeNbaSource(
        team_logs={"2024-25": team_rows},
        player_logs={"2024-25": load_fixture("player_game_log_neutral.json")},
        summaries={
            NEUTRAL_GAME_ID: _valid_summary(),
            SECOND_AMBIGUOUS_ID: {**_valid_summary(SECOND_AMBIGUOUS_ID), "gameStatus": 2},
        },
    )
    result = run_games_pipeline(_neutral_config(test_config), source=source, store=store)
    assert result.success is False
    assert store.completed == []
    first = store.checkpoints[("GAMES_STATS", "2024-25", f"box_score_summary:{NEUTRAL_GAME_ID}")]
    assert first.status == "FETCHED"
    assert first.payload["gameId"] == NEUTRAL_GAME_ID
    second_key = ("GAMES_STATS", "2024-25", f"box_score_summary:{SECOND_AMBIGUOUS_ID}")
    assert second_key not in store.checkpoints


def test_neutral_retry_succeeds_with_every_http_method_raising(test_config) -> None:
    store = FakeGamesStore(fail_persist=RuntimeError("forced TX2 failure"))
    first = run_games_pipeline(
        _neutral_config(test_config),
        source=_ambiguous_only_source(),
        store=store,
    )
    assert first.success is False
    summary_key = ("GAMES_STATS", "2024-25", f"box_score_summary:{NEUTRAL_GAME_ID}")
    assert store.checkpoints[summary_key].status == "FETCHED"

    store.fail_persist = None
    raising = _ambiguous_only_source(
        fail_team_log=RuntimeError("HTTP should not be called"),
        fail_player_log=RuntimeError("HTTP should not be called"),
        fail_summary=RuntimeError("HTTP should not be called"),
    )
    second = run_games_pipeline(_neutral_config(test_config), source=raising, store=store)
    assert second.success is True
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
    assert raising.summary_calls == []
    assert store.checkpoints[summary_key].status == "PERSISTED"
    assert store.checkpoints[summary_key].payload is None
    game = next(item for item in store.games if item.nba_game_id == NEUTRAL_GAME_ID)
    assert game.home_nba_team_id == GSW
    assert game.away_nba_team_id == BOS


def test_tx2_marks_and_clears_only_used_checkpoints(test_config) -> None:
    from splitedge_importer.models import CheckpointRecord
    from splitedge_importer.retrieval.box_score_summary import summary_resource

    store = FakeGamesStore()
    stale_resource = summary_resource(STALE_SUMMARY_ID)
    store.checkpoints[("GAMES_STATS", "2024-25", stale_resource)] = CheckpointRecord(
        import_type="GAMES_STATS",
        season="2024-25",
        resource=stale_resource,
        status="FETCHED",
        row_count=1,
        payload={"gameId": STALE_SUMMARY_ID, "unused": True},
    )
    result = run_games_pipeline(
        _neutral_config(test_config),
        source=_ambiguous_only_source(),
        store=store,
    )
    assert result.success is True
    used_summary = store.checkpoints[
        ("GAMES_STATS", "2024-25", f"box_score_summary:{NEUTRAL_GAME_ID}")
    ]
    assert used_summary.status == "PERSISTED"
    assert used_summary.payload is None
    stale = store.checkpoints[("GAMES_STATS", "2024-25", stale_resource)]
    assert stale.status == "FETCHED"
    assert stale.payload == {"gameId": STALE_SUMMARY_ID, "unused": True}
    assert store.checkpoints[("GAMES_STATS", "2024-25", "team_game_log")].status == "PERSISTED"
    assert store.checkpoints[("GAMES_STATS", "2024-25", "player_game_log")].status == "PERSISTED"


def test_stale_unused_summary_untouched_on_ordinary_run(test_config) -> None:
    from splitedge_importer.models import CheckpointRecord
    from splitedge_importer.retrieval.box_score_summary import summary_resource

    store = FakeGamesStore()
    stale_resource = summary_resource(STALE_SUMMARY_ID)
    store.checkpoints[("GAMES_STATS", "2023-24", stale_resource)] = CheckpointRecord(
        import_type="GAMES_STATS",
        season="2023-24",
        resource=stale_resource,
        status="FETCHED",
        row_count=1,
        payload={"gameId": STALE_SUMMARY_ID},
    )
    source = FakeNbaSource()
    result = run_games_pipeline(test_config, source=source, store=store)
    assert result.success is True
    assert source.summary_calls == []
    stale = store.checkpoints[("GAMES_STATS", "2023-24", stale_resource)]
    assert stale.status == "FETCHED"
    assert stale.payload == {"gameId": STALE_SUMMARY_ID}

