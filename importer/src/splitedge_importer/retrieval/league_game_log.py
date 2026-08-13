"""LeagueGameLog column contracts shared by retrieval and checkpoints."""

from __future__ import annotations

TEAM_LOG_COLUMNS = frozenset(
    {"GAME_ID", "TEAM_ID", "TEAM_ABBREVIATION", "GAME_DATE", "MATCHUP", "WL", "PTS"}
)
PLAYER_LOG_COLUMNS = TEAM_LOG_COLUMNS | {
    "PLAYER_ID",
    "PLAYER_NAME",
    "MIN",
    "REB",
    "AST",
    "FG3M",
}

REQUIRED_COLUMNS = {
    "team_game_log": TEAM_LOG_COLUMNS,
    "player_game_log": PLAYER_LOG_COLUMNS,
}


def required_columns_for(resource: str) -> frozenset[str]:
    return REQUIRED_COLUMNS[resource]
