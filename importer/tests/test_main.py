from splitedge_importer.main import run


def test_run_reports_foundation_status() -> None:
    assert run() == "SplitEdge importer foundation ready"
