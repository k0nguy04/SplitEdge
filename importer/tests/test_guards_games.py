from datetime import date
from decimal import Decimal

from splitedge_importer.guards_games import evaluate_games_guards
from splitedge_importer.models import NormalizedGame, NormalizedPlayerGameStat


def _game(game_id: str = "0022300001", season: str = "2023-24") -> NormalizedGame:
    return NormalizedGame(
        nba_game_id=game_id,
        season=season,
        game_date=date(2023, 10, 24),
        home_nba_team_id=1,
        away_nba_team_id=2,
        home_score=100,
        away_score=90,
    )


def _stat(
    *,
    game_id: str = "0022300001",
    season: str = "2023-24",
    player_id: int = 10,
    team_id: int = 1,
) -> NormalizedPlayerGameStat:
    return NormalizedPlayerGameStat(
        nba_game_id=game_id,
        season=season,
        nba_player_id=player_id,
        nba_team_id=team_id,
        minutes=Decimal("20.000"),
        points=10,
        rebounds=2,
        assists=3,
        three_pointers_made=1,
        player_name="Player",
    )


def test_teams_below_minimum_blocks_persist() -> None:
    result = evaluate_games_guards(
        team_count=1,
        min_teams=30,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    assert result.passed is False
    assert result.reason == "teams_below_minimum"


def test_duplicate_ids_block_persist() -> None:
    game_dup = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=True,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    stat_dup = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=True,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    assert game_dup.reason == "duplicate_game_ids"
    assert stat_dup.reason == "duplicate_player_stat_ids"


def test_empty_persist_set_blocks() -> None:
    result = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[],
        stats=[],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    assert result.reason == "empty_persist_set"


def test_broken_reference_blocks_persist() -> None:
    result = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat(game_id="0022300999")],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    assert result.reason == "invariant_broken_reference"


def test_season_minimums_apply_after_normalization() -> None:
    games_low = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=5,
        min_player_stats_per_season=1,
    )
    stats_low = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=5,
    )
    assert games_low.reason == "games_below_minimum"
    assert stats_low.reason == "stats_below_minimum"


def test_healthy_fixture_batch_passes() -> None:
    result = evaluate_games_guards(
        team_count=2,
        min_teams=1,
        games=[_game()],
        stats=[_stat()],
        known_team_ids={1, 2},
        duplicate_game_ids=False,
        duplicate_stat_ids=False,
        seasons=("2023-24",),
        min_games_per_season=1,
        min_player_stats_per_season=1,
    )
    assert result.passed is True
    assert result.reason is None
