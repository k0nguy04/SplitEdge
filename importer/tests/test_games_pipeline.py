import sys
from dataclasses import replace

from conftest import FakeGamesStore, FakeNbaSource

from splitedge_importer.games_pipeline import run_games_pipeline


def test_happy_path_persists_games_and_stats(test_config) -> None:
    store = FakeGamesStore()
    result = run_games_pipeline(test_config, source=FakeNbaSource(), store=store)
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
    )
    second = run_games_pipeline(test_config, source=raising, store=store)
    assert second.success is True
    assert second.status == "COMPLETED"
    assert raising.team_log_calls == []
    assert raising.player_log_calls == []
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
