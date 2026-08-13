from datetime import date

from conftest import KNOWN_TEAM_IDS, load_fixture

from splitedge_importer.models import AmbiguousGame
from splitedge_importer.normalization.games import (
    complete_ambiguous_game,
    normalize_game_id,
    normalize_games,
)


def test_pairs_home_and_away_from_matchup() -> None:
    games, ambiguous, rejected, skipped, duplicate = normalize_games(
        load_fixture("team_game_log_2023_24.json"),
        season="2023-24",
        known_team_ids=KNOWN_TEAM_IDS,
    )
    assert duplicate is False
    assert skipped == 2
    assert ambiguous == []
    assert [item.reason for item in rejected] == []
    first = next(game for game in games if game.nba_game_id == "0022300001")
    assert first.home_nba_team_id == 1610612744
    assert first.away_nba_team_id == 1610612738
    assert first.home_score == 122
    assert first.away_score == 114
    assert first.game_date == date(2023, 10, 24)
    assert first.status == "FINAL"
    assert first.season == "2023-24"
    assert {game.nba_game_id for game in games} == {"0022300001", "0022300002"}


def test_skips_non_regular_season_prefix() -> None:
    _, _, _, skipped, _ = normalize_games(
        load_fixture("team_game_log_2023_24.json"),
        season="2023-24",
        known_team_ids=KNOWN_TEAM_IDS,
    )
    assert skipped == 2


def test_zero_pads_numeric_game_ids() -> None:
    assert normalize_game_id(22300001) == "0022300001"
    assert normalize_game_id("0022300001") == "0022300001"


def test_rejects_unpaired_game() -> None:
    rows = [
        {
            "GAME_ID": "0022300999",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2023-11-01",
            "MATCHUP": "GSW vs. BOS",
            "WL": "W",
            "PTS": 100,
        }
    ]
    games, ambiguous, rejected, skipped, duplicate = normalize_games(
        rows, season="2023-24", known_team_ids=KNOWN_TEAM_IDS
    )
    assert games == []
    assert ambiguous == []
    assert skipped == 0
    assert duplicate is False
    assert rejected[0].reason == "unpaired_game"
    assert rejected[0].season == "2023-24"
    assert rejected[0].nba_game_id == "0022300999"


def test_rejects_incomplete_wl() -> None:
    rows = [
        {
            "GAME_ID": "0022300998",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2023-11-01",
            "MATCHUP": "GSW vs. BOS",
            "WL": None,
            "PTS": 100,
        },
        {
            "GAME_ID": "0022300998",
            "TEAM_ID": 1610612738,
            "TEAM_ABBREVIATION": "BOS",
            "GAME_DATE": "2023-11-01",
            "MATCHUP": "BOS @ GSW",
            "WL": None,
            "PTS": 90,
        },
    ]
    games, ambiguous, rejected, _, _ = normalize_games(
        rows, season="2023-24", known_team_ids=KNOWN_TEAM_IDS
    )
    assert games == []
    assert ambiguous == []
    assert rejected[0].reason == "incomplete_game"


def test_rejects_unknown_team() -> None:
    rows = [
        {
            "GAME_ID": "0022300997",
            "TEAM_ID": 1,
            "TEAM_ABBREVIATION": "XXX",
            "GAME_DATE": "2023-11-01",
            "MATCHUP": "XXX vs. BOS",
            "WL": "W",
            "PTS": 100,
        },
        {
            "GAME_ID": "0022300997",
            "TEAM_ID": 1610612738,
            "TEAM_ABBREVIATION": "BOS",
            "GAME_DATE": "2023-11-01",
            "MATCHUP": "BOS @ XXX",
            "WL": "L",
            "PTS": 90,
        },
    ]
    games, ambiguous, rejected, _, _ = normalize_games(
        rows, season="2023-24", known_team_ids=KNOWN_TEAM_IDS
    )
    assert games == []
    assert ambiguous == []
    assert any(item.reason == "unknown_team" for item in rejected)


def test_duplicate_game_team_rows_set_flag() -> None:
    rows = load_fixture("team_game_log_2024_25.json")
    rows = rows + [dict(rows[0])]
    games, ambiguous, rejected, _, duplicate = normalize_games(
        rows, season="2024-25", known_team_ids=KNOWN_TEAM_IDS
    )
    assert duplicate is True
    assert ambiguous == []
    assert any(item.reason == "duplicate_id" for item in rejected)
    assert len(games) == 1


def test_both_vs_completed_game_is_ambiguous() -> None:
    games, ambiguous, rejected, skipped, duplicate = normalize_games(
        load_fixture("team_game_log_neutral.json"),
        season="2024-25",
        known_team_ids=KNOWN_TEAM_IDS,
    )
    assert games == []
    assert rejected == []
    assert skipped == 0
    assert duplicate is False
    assert len(ambiguous) == 1
    item = ambiguous[0]
    assert isinstance(item, AmbiguousGame)
    assert item.nba_game_id == "0022400999"
    assert item.season == "2024-25"
    assert item.game_date == date(2024, 11, 2)
    team_ids = {row["TEAM_ID"] for row in item.team_rows}
    assert team_ids == {1610612738, 1610612744}


def test_both_at_completed_game_is_ambiguous() -> None:
    rows = [
        {
            "GAME_ID": "0022400888",
            "TEAM_ID": 1610612738,
            "TEAM_ABBREVIATION": "BOS",
            "GAME_DATE": "2024-11-03",
            "MATCHUP": "BOS @ GSW",
            "WL": "L",
            "PTS": 99,
        },
        {
            "GAME_ID": "0022400888",
            "TEAM_ID": 1610612744,
            "TEAM_ABBREVIATION": "GSW",
            "GAME_DATE": "2024-11-03",
            "MATCHUP": "GSW @ BOS",
            "WL": "W",
            "PTS": 104,
        },
    ]
    games, ambiguous, rejected, _, _ = normalize_games(
        rows, season="2024-25", known_team_ids=KNOWN_TEAM_IDS
    )
    assert games == []
    assert rejected == []
    assert len(ambiguous) == 1
    assert ambiguous[0].nba_game_id == "0022400888"


def test_both_vs_incomplete_wl_is_ordinary_rejection() -> None:
    rows = load_fixture("team_game_log_neutral.json")
    for row in rows:
        row["WL"] = None
    games, ambiguous, rejected, _, _ = normalize_games(
        rows, season="2024-25", known_team_ids=KNOWN_TEAM_IDS
    )
    assert games == []
    assert ambiguous == []
    assert rejected[0].reason == "incomplete_game"


def test_complete_ambiguous_game_maps_by_team_id_not_row_order() -> None:
    _, ambiguous, _, _, _ = normalize_games(
        load_fixture("team_game_log_neutral.json"),
        season="2024-25",
        known_team_ids=KNOWN_TEAM_IDS,
    )
    game = complete_ambiguous_game(ambiguous[0], 1610612744, 1610612738)
    assert game.home_nba_team_id == 1610612744
    assert game.away_nba_team_id == 1610612738
    assert game.home_score == 118
    assert game.away_score == 110
    assert ambiguous[0].team_rows[0]["TEAM_ID"] == 1610612738
