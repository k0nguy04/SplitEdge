import pytest
from conftest import IntegrationDatabaseError, require_integration_database


def test_refuses_primary_splitedge_database() -> None:
    with pytest.raises(IntegrationDatabaseError, match="splitedge_test"):
        require_integration_database(
            "postgresql://splitedge:splitedge_local@localhost:5432/splitedge"
        )


def test_allows_splitedge_test_database() -> None:
    name = require_integration_database(
        "postgresql://splitedge:splitedge_local@localhost:5432/splitedge_test"
    )
    assert name == "splitedge_test"


def test_does_not_honor_allow_primary_override(monkeypatch) -> None:
    monkeypatch.setenv("SPLITEDGE_ALLOW_PRIMARY_DB", "1")
    with pytest.raises(IntegrationDatabaseError):
        require_integration_database(
            "postgresql://splitedge:splitedge_local@localhost:5432/splitedge"
        )
