from conftest import load_fixture

from splitedge_importer.normalization.players import normalize_players
from splitedge_importer.normalization.teams import normalize_teams
from splitedge_importer.validation.records import is_active_roster_status, parse_positive_id, trim


def test_trim_and_parse_helpers() -> None:
    assert trim("  abc  ") == "abc"
    assert parse_positive_id("201939") == 201939
    assert parse_positive_id(0) is None
    assert parse_positive_id(None) is None
    assert is_active_roster_status(1) is True
    assert is_active_roster_status("1") is True
    assert is_active_roster_status(0) is False
    assert is_active_roster_status("0") is False


def test_duplicate_player_ids_keep_first_and_reject_extra() -> None:
    valid, rejected, _, _, duplicate = normalize_players(
        load_fixture("players.json"),
        {1610612744, 1610612747},
    )
    assert duplicate is True
    assert sum(1 for player in valid if player.nba_player_id == 201939) == 1
    assert any(item.reason == "duplicate_id" and item.entity == "player" for item in rejected)


def test_duplicate_team_ids_keep_first_and_reject_extra() -> None:
    rows = load_fixture("teams.json") + [
        {
            "id": 1610612744,
            "full_name": "Golden State Duplicate",
            "abbreviation": "GSW",
            "nickname": "Warriors",
            "city": "Golden State",
        }
    ]
    valid, rejected, duplicate = normalize_teams(rows)
    assert duplicate is True
    assert sum(1 for team in valid if team.nba_team_id == 1610612744) == 1
    assert any(item.reason == "duplicate_id" and item.entity == "team" for item in rejected)
