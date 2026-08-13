from splitedge_importer.guards import evaluate_guards
from splitedge_importer.models import NormalizedPlayer, NormalizedTeam


def _team(nba_team_id: int) -> NormalizedTeam:
    return NormalizedTeam(
        nba_team_id=nba_team_id,
        abbreviation="AAA",
        full_name="Team",
        nickname="Team",
        city="City",
    )


def _player(nba_player_id: int) -> NormalizedPlayer:
    return NormalizedPlayer(
        nba_player_id=nba_player_id,
        first_name="A",
        last_name="B",
        full_name="A B",
        is_active=True,
        nba_team_id=1,
    )


def test_empty_players_block_deactivation() -> None:
    result = evaluate_guards(
        teams=[_team(1)],
        players=[],
        duplicate_team_ids=False,
        duplicate_player_ids=False,
        min_teams=1,
        min_active_players=1,
    )
    assert result.passed is False
    assert result.reason == "players_empty"


def test_empty_teams_block_persist() -> None:
    result = evaluate_guards(
        teams=[],
        players=[_player(1)],
        duplicate_team_ids=False,
        duplicate_player_ids=False,
        min_teams=1,
        min_active_players=1,
    )
    assert result.passed is False
    assert result.reason == "teams_empty"


def test_player_count_below_minimum() -> None:
    result = evaluate_guards(
        teams=[_team(1)],
        players=[_player(1)],
        duplicate_team_ids=False,
        duplicate_player_ids=False,
        min_teams=1,
        min_active_players=5,
    )
    assert result.passed is False
    assert result.reason == "players_below_minimum"


def test_team_count_below_minimum() -> None:
    result = evaluate_guards(
        teams=[_team(1)],
        players=[_player(1)],
        duplicate_team_ids=False,
        duplicate_player_ids=False,
        min_teams=30,
        min_active_players=1,
    )
    assert result.passed is False
    assert result.reason == "teams_below_minimum"


def test_duplicate_ids_fail_guard() -> None:
    player_dup = evaluate_guards(
        teams=[_team(1)],
        players=[_player(1)],
        duplicate_team_ids=False,
        duplicate_player_ids=True,
        min_teams=1,
        min_active_players=1,
    )
    team_dup = evaluate_guards(
        teams=[_team(1)],
        players=[_player(1)],
        duplicate_team_ids=True,
        duplicate_player_ids=False,
        min_teams=1,
        min_active_players=1,
    )
    assert player_dup.reason == "duplicate_player_ids"
    assert team_dup.reason == "duplicate_team_ids"


def test_healthy_fixture_batch_passes_with_test_minimums() -> None:
    result = evaluate_guards(
        teams=[_team(1), _team(2)],
        players=[_player(1), _player(2)],
        duplicate_team_ids=False,
        duplicate_player_ids=False,
        min_teams=1,
        min_active_players=1,
    )
    assert result.passed is True
    assert result.reason is None
