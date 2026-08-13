"""NBA source protocol. Retrieval stays separate from normalization."""

from __future__ import annotations

from typing import Protocol


class NbaSource(Protocol):
    def fetch_teams(self) -> list[dict]:
        """Return raw team dictionaries from nba_api or a test double."""

    def fetch_active_players(self, season: str) -> list[dict]:
        """Return raw CommonAllPlayers rows for ``season``."""

    def fetch_team_game_log(self, season: str) -> list[dict]:
        """Return LeagueGameLog team-mode rows for ``season``."""

    def fetch_player_game_log(self, season: str) -> list[dict]:
        """Return LeagueGameLog player-mode rows for ``season``."""
