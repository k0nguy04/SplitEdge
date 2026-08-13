import sys

from conftest import FakeImportStore, FakeNbaSource, load_fixture

from splitedge_importer.pipeline import run_pipeline


def test_happy_path_persists_valid_rows(test_config) -> None:
    source = FakeNbaSource()
    store = FakeImportStore()
    result = run_pipeline(test_config, source=source, store=store)
    assert result.success is True
    assert result.status == "COMPLETED"
    assert result.records_processed == 6
    assert result.records_failed == 0
    assert store.completed
    assert len(store.teams) == 3
    assert len(store.players) == 3


def test_running_is_inserted_before_retrieve(test_config) -> None:
    store = FakeImportStore()
    seen: dict[str, list[int]] = {}

    class OrderedSource(FakeNbaSource):
        def fetch_teams(self) -> list[dict]:
            seen["running"] = list(store.running_ids)
            return super().fetch_teams()

    result = run_pipeline(test_config, source=OrderedSource(), store=store)
    assert result.success is True
    assert seen["running"] == [1]


def test_null_team_player_is_warning_not_failed(test_config) -> None:
    players = load_fixture("players_valid.json") + [
        {
            "PERSON_ID": 1629029,
            "DISPLAY_FIRST_LAST": "Luka Doncic",
            "ROSTERSTATUS": 1,
            "TEAM_ID": 0,
        }
    ]
    store = FakeImportStore()
    result = run_pipeline(test_config, source=FakeNbaSource(players=players), store=store)
    assert result.success is True
    assert result.records_failed == 0
    assert result.details["players"]["warning"] == 1
    assert result.details["players"]["persisted"] == 4
    assert result.records_processed == 7


def test_guard_failure_does_not_persist(test_config) -> None:
    store = FakeImportStore()
    result = run_pipeline(
        test_config,
        source=FakeNbaSource(players=load_fixture("players.json")),
        store=store,
    )
    assert result.success is False
    assert result.status == "FAILED"
    assert store.completed == []
    assert store.failed
    assert result.details["guard"]["reason"] == "duplicate_player_ids"
    assert result.records_processed == 0
    assert result.records_failed >= 1


def test_empty_player_batch_does_not_persist(test_config) -> None:
    store = FakeImportStore()
    result = run_pipeline(
        test_config,
        source=FakeNbaSource(players=[]),
        store=store,
    )
    assert result.success is False
    assert result.details["guard"]["reason"] == "players_empty"
    assert store.completed == []


def test_persist_failure_marks_failed_and_does_not_keep_data(test_config) -> None:
    store = FakeImportStore(fail_persist=RuntimeError("persist failed"))
    result = run_pipeline(test_config, source=FakeNbaSource(), store=store)
    assert result.success is False
    assert result.status == "FAILED"
    assert store.teams == []
    assert store.failed
    assert "persist failed" in (result.error_message or "")


def test_retrieve_failure_marks_failed(test_config) -> None:
    store = FakeImportStore()
    result = run_pipeline(
        test_config,
        source=FakeNbaSource(fail_players=RuntimeError("stats.nba.com unavailable")),
        store=store,
    )
    assert result.success is False
    assert store.completed == []
    assert store.failed[0]["run_id"] == store.running_ids[0]


def test_pipeline_does_not_import_nba_api(test_config) -> None:
    sys.modules.pop("nba_api", None)
    sys.modules.pop("splitedge_importer.retrieval.nba_api_client", None)
    run_pipeline(test_config, source=FakeNbaSource(), store=FakeImportStore())
    assert "nba_api" not in sys.modules
    assert "splitedge_importer.retrieval.nba_api_client" not in sys.modules


def test_details_include_required_entity_counts(test_config) -> None:
    result = run_pipeline(test_config, source=FakeNbaSource(), store=FakeImportStore())
    for entity in ("teams", "players"):
        for key in ("received", "persisted", "rejected", "warning", "deactivation"):
            assert key in result.details[entity]
