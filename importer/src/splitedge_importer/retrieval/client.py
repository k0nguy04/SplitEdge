"""NBA source protocol. Retrieval stays separate from normalization."""

from __future__ import annotations

from typing import Protocol


class NbaSource(Protocol):
    def fetch_teams(self) -> list[dict]:
        """Return raw team dictionaries from nba_api or a test double."""

    def fetch_active_players(self, season: str) -> list[dict]:
        """Return raw CommonAllPlayers rows for ``season``."""
