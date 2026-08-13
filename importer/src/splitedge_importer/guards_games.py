"""Guards that block games/stats persistence."""

from __future__ import annotations

from collections import Counter

from splitedge_importer.models import GuardResult, NormalizedGame, NormalizedPlayerGameStat


def evaluate_games_guards(
    *,
    team_count: int,
    min_teams: int,
    games: list[NormalizedGame],
    stats: list[NormalizedPlayerGameStat],
    known_team_ids: set[int],
    duplicate_game_ids: bool,
    duplicate_stat_ids: bool,
    seasons: tuple[str, ...],
    min_games_per_season: int,
    min_player_stats_per_season: int,
) -> GuardResult:
    if team_count < min_teams:
        return GuardResult(False, "teams_below_minimum")
    if duplicate_game_ids:
        return GuardResult(False, "duplicate_game_ids")
    if duplicate_stat_ids:
        return GuardResult(False, "duplicate_player_stat_ids")
    if not games or not stats:
        return GuardResult(False, "empty_persist_set")

    games_by_id = {game.nba_game_id: game for game in games}
    for stat in stats:
        game = games_by_id.get(stat.nba_game_id)
        if game is None or game.season != stat.season:
            return GuardResult(False, "invariant_broken_reference")
        if stat.nba_team_id not in known_team_ids:
            return GuardResult(False, "invariant_broken_reference")
        if stat.nba_team_id not in {game.home_nba_team_id, game.away_nba_team_id}:
            return GuardResult(False, "invariant_broken_reference")
        home_known = game.home_nba_team_id in known_team_ids
        away_known = game.away_nba_team_id in known_team_ids
        if not home_known or not away_known:
            return GuardResult(False, "invariant_broken_reference")

    games_by_season = Counter(game.season for game in games)
    stats_by_season = Counter(stat.season for stat in stats)
    for season in seasons:
        if games_by_season[season] < min_games_per_season:
            return GuardResult(False, "games_below_minimum")
        if stats_by_season[season] < min_player_stats_per_season:
            return GuardResult(False, "stats_below_minimum")
    return GuardResult(True, None)
