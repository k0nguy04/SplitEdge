"""Batch guards that block persistence and player deactivation."""

from __future__ import annotations

from splitedge_importer.models import GuardResult, NormalizedPlayer, NormalizedTeam


def evaluate_guards(
    *,
    teams: list[NormalizedTeam],
    players: list[NormalizedPlayer],
    duplicate_team_ids: bool,
    duplicate_player_ids: bool,
    min_teams: int,
    min_active_players: int,
) -> GuardResult:
    if duplicate_team_ids:
        return GuardResult(False, "duplicate_team_ids")
    if duplicate_player_ids:
        return GuardResult(False, "duplicate_player_ids")

    unique_teams = {team.nba_team_id for team in teams}
    unique_players = {player.nba_player_id for player in players}

    if not unique_teams:
        return GuardResult(False, "teams_empty")
    if not unique_players:
        return GuardResult(False, "players_empty")
    if len(unique_teams) < min_teams:
        return GuardResult(False, "teams_below_minimum")
    if len(unique_players) < min_active_players:
        return GuardResult(False, "players_below_minimum")
    return GuardResult(True, None)
