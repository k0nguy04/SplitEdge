from conftest import FakeImportStore, FakeNbaSource, load_fixture

from splitedge_importer.pipeline import run_pipeline


def test_completed_run_has_timestamps_counts_and_details(test_config) -> None:
    store = FakeImportStore()
    result = run_pipeline(test_config, source=FakeNbaSource(), store=store)
    completed = store.completed[0]
    assert completed["run_id"] == result.run_id
    assert completed["records_processed"] == 6
    assert completed["records_failed"] == 0
    assert completed["details"]["season"] == "2025-26"
    assert completed["details"]["teams"]["received"] == 3
    assert completed["details"]["players"]["received"] == 3


def test_failed_run_records_error_and_zero_processed(test_config) -> None:
    store = FakeImportStore()
    result = run_pipeline(
        test_config,
        source=FakeNbaSource(fail_teams=RuntimeError("timeout talking to NBA")),
        store=store,
    )
    failed = store.failed[0]
    assert failed["run_id"] == result.run_id
    assert failed["records_processed"] == 0
    assert "timeout talking to NBA" in (failed["error_message"] or "")
    assert failed["details"]["stage"] == "retrieve_teams"


def test_validation_rejections_are_records_failed(test_config) -> None:
    teams = load_fixture("teams.json") + [
        {"id": None, "full_name": "Bad", "abbreviation": "BAD", "nickname": "Bad", "city": "X"}
    ]
    store = FakeImportStore()
    result = run_pipeline(
        test_config,
        source=FakeNbaSource(teams=teams, players=load_fixture("players_valid.json")),
        store=store,
    )
    assert result.success is True
    assert result.records_failed == 1
    assert result.records_processed == 6
    assert result.details["teams"]["rejected"] == 1
