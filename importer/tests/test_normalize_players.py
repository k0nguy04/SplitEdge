from conftest import load_fixture

from splitedge_importer.normalization.players import normalize_players

KNOWN_TEAMS = {1610612744, 1610612738, 1610612747}


def test_active_player_is_persist_candidate() -> None:
    valid, rejected, warnings, skipped, duplicate = normalize_players(
        load_fixture("players_valid.json"),
        KNOWN_TEAMS,
    )
    assert duplicate is False
    assert rejected == []
    assert skipped == 0
    curry = next(player for player in valid if player.nba_player_id == 201939)
    assert curry.is_active is True
    assert curry.full_name == "Stephen Curry"
    assert curry.first_name == "Stephen"
    assert curry.last_name == "Curry"
    assert curry.nba_team_id == 1610612744


def test_inactive_roster_status_is_skipped_not_rejected() -> None:
    valid, rejected, _, skipped, _ = normalize_players(load_fixture("players.json"), KNOWN_TEAMS)
    assert skipped == 1
    assert all(player.nba_player_id != 203507 for player in valid)
    assert all(item.reason != "inactive" for item in rejected)


def test_zero_team_id_is_warning_not_failure() -> None:
    valid, rejected, warnings, _, _ = normalize_players(load_fixture("players.json"), KNOWN_TEAMS)
    luka = next(player for player in valid if player.nba_player_id == 1629029)
    assert luka.nba_team_id is None
    assert any(item.nba_player_id == 1629029 and item.reason == "missing_team" for item in warnings)
    assert all(item.reason != "missing_team" for item in rejected)


def test_unknown_team_is_warning() -> None:
    rows = [
        {
            "PERSON_ID": 99,
            "DISPLAY_FIRST_LAST": "Unknown Team Player",
            "ROSTERSTATUS": 1,
            "TEAM_ID": 1610612749,
        }
    ]
    valid, rejected, warnings, _, _ = normalize_players(rows, KNOWN_TEAMS)
    assert valid[0].nba_team_id is None
    assert rejected == []
    assert warnings[0].reason == "unknown_team"


def test_person_id_is_unchanged() -> None:
    valid, _, _, _, _ = normalize_players(
        [
            {
                "PERSON_ID": 2544,
                "DISPLAY_FIRST_LAST": "LeBron James",
                "ROSTERSTATUS": "1",
                "TEAM_ID": 1610612747,
            }
        ],
        KNOWN_TEAMS,
    )
    assert valid[0].nba_player_id == 2544


def test_missing_person_id_is_rejected() -> None:
    _, rejected, _, _, _ = normalize_players(load_fixture("players.json"), KNOWN_TEAMS)
    assert any(item.reason == "missing_id" for item in rejected)
