from decimal import Decimal

from conftest import KNOWN_TEAM_IDS, load_fixture

from splitedge_importer.models import NormalizedGame
from splitedge_importer.normalization.games import normalize_games
from splitedge_importer.normalization.player_stats import (
    normalize_player_stats,
    stubs_for_unknown_players,
)


def _games() -> list[NormalizedGame]:
    games, _, _, _ = normalize_games(
        load_fixture("team_game_log_2023_24.json"),
        season="2023-24",
        known_team_ids=KNOWN_TEAM_IDS,
    )
    return games


def test_maps_minutes_and_counting_stats() -> None:
    stats, rejected, skipped_dnp, skipped_non_regular, duplicate = normalize_player_stats(
        load_fixture("player_game_log_2023_24.json"),
        season="2023-24",
        games=_games(),
        known_team_ids=KNOWN_TEAM_IDS,
    )
    assert duplicate is False
    assert skipped_dnp == 1
    assert skipped_non_regular == 1
    assert any(item.reason == "malformed_minutes" for item in rejected)
    assert any(item.reason == "unknown_team" for item in rejected)
    curry = next(
        stat
        for stat in stats
        if stat.nba_player_id == 201939 and stat.nba_game_id == "0022300001"
    )
    assert curry.minutes == Decimal("36.500")
    assert curry.points == 30
    assert curry.rebounds == 5
    assert curry.assists == 8
    assert curry.three_pointers_made == 4
    assert curry.nba_team_id == 1610612744


def test_skips_dnp_without_counting_as_rejection() -> None:
    _, rejected, skipped_dnp, _, _ = normalize_player_stats(
        load_fixture("player_game_log_2023_24.json"),
        season="2023-24",
        games=_games(),
        known_team_ids=KNOWN_TEAM_IDS,
    )
    assert skipped_dnp == 1
    assert all(item.reason != "skipped_dnp" for item in rejected)


def test_trade_keeps_game_night_team() -> None:
    stats, _, _, _, _ = normalize_player_stats(
        load_fixture("player_game_log_2023_24.json"),
        season="2023-24",
        games=_games(),
        known_team_ids=KNOWN_TEAM_IDS,
    )
    tatum_teams = {
        stat.nba_team_id for stat in stats if stat.nba_player_id == 1628369
    }
    assert tatum_teams == {1610612738, 1610612747}


def test_stub_copies_full_name_to_first_and_last() -> None:
    stats, _, _, _, _ = normalize_player_stats(
        load_fixture("player_game_log_2023_24.json"),
        season="2023-24",
        games=_games(),
        known_team_ids=KNOWN_TEAM_IDS,
    )
    stubs = stubs_for_unknown_players(stats, known_player_ids={201939, 2544, 1628369})
    stub = next(item for item in stubs if item.nba_player_id == 777777)
    assert stub.full_name == "Marcus Historical"
    assert stub.first_name == "Marcus Historical"
    assert stub.last_name == "Marcus Historical"


def test_rejects_blank_or_overlong_player_name() -> None:
    games = _games()
    rows = [
        {
            "GAME_ID": "0022300001",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2023-10-24",
            "MATCHUP": "GSW vs. BOS",
            "WL": "W",
            "PTS": 1,
            "PLAYER_ID": 42,
            "PLAYER_NAME": "   ",
            "MIN": "10:00",
            "REB": 0,
            "AST": 0,
            "FG3M": 0,
        },
        {
            "GAME_ID": "0022300001",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2023-10-24",
            "MATCHUP": "GSW vs. BOS",
            "WL": "W",
            "PTS": 1,
            "PLAYER_ID": 43,
            "PLAYER_NAME": "A" * 65,
            "MIN": "10:00",
            "REB": 0,
            "AST": 0,
            "FG3M": 0,
        },
    ]
    _, rejected, _, _, _ = normalize_player_stats(
        rows, season="2023-24", games=games, known_team_ids=KNOWN_TEAM_IDS
    )
    reasons = {item.reason for item in rejected}
    assert "missing_name" in reasons
    assert "name_too_long" in reasons


def test_stat_must_match_same_season_game_home_or_away() -> None:
    games = _games()
    rows = [
        {
            "GAME_ID": "0022300001",
            "TEAM_ID": 1610612747,
            "TEAM_ABBREVIATION": "LAL",
            "GAME_DATE": "2023-10-24",
            "MATCHUP": "LAL @ GSW",
            "WL": "L",
            "PTS": 8,
            "PLAYER_ID": 2544,
            "PLAYER_NAME": "LeBron James",
            "MIN": "20:00",
            "REB": 2,
            "AST": 2,
            "FG3M": 0,
        }
    ]
    stats, rejected, _, _, _ = normalize_player_stats(
        rows, season="2023-24", games=games, known_team_ids=KNOWN_TEAM_IDS
    )
    assert stats == []
    assert rejected[0].reason == "team_not_in_game"


def test_unknown_game_is_rejected() -> None:
    rows = [
        {
            "GAME_ID": "0022300999",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2023-10-24",
            "MATCHUP": "GSW vs. BOS",
            "WL": "W",
            "PTS": 8,
            "PLAYER_ID": 201939,
            "PLAYER_NAME": "Stephen Curry",
            "MIN": "20:00",
            "REB": 2,
            "AST": 2,
            "FG3M": 1,
        }
    ]
    stats, rejected, _, _, _ = normalize_player_stats(
        rows, season="2023-24", games=_games(), known_team_ids=KNOWN_TEAM_IDS
    )
    assert stats == []
    assert rejected[0].reason == "unknown_game"
