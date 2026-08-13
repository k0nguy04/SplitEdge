from conftest import load_fixture

from splitedge_importer.normalization.teams import normalize_teams


def test_maps_static_team_fields() -> None:
    valid, rejected, duplicate = normalize_teams(load_fixture("teams.json"))
    assert duplicate is False
    assert rejected == []
    warriors = next(team for team in valid if team.nba_team_id == 1610612744)
    assert warriors.abbreviation == "GSW"
    assert warriors.full_name == "Golden State Warriors"
    assert warriors.nickname == "Warriors"
    assert warriors.city == "Golden State"


def test_trims_whitespace() -> None:
    valid, rejected, _ = normalize_teams(
        [
            {
                "id": 1610612744,
                "full_name": "  Golden State Warriors  ",
                "abbreviation": " GSW ",
                "nickname": " Warriors ",
                "city": " Golden State ",
            }
        ]
    )
    assert rejected == []
    assert valid[0].full_name == "Golden State Warriors"
    assert valid[0].abbreviation == "GSW"


def test_rejects_missing_id() -> None:
    _, rejected, _ = normalize_teams(
        [
            {
                "id": None,
                "full_name": "Ghosts",
                "abbreviation": "GHO",
                "nickname": "Ghosts",
                "city": "X",
            }
        ]
    )
    assert rejected[0].reason == "missing_id"


def test_rejects_missing_full_name() -> None:
    _, rejected, _ = normalize_teams(
        [{"id": 1, "full_name": "  ", "abbreviation": "GHO", "nickname": "Ghosts", "city": "X"}]
    )
    assert rejected[0].reason == "missing_name"
