"""Normalize LeagueGameLog team-mode rows into completed regular-season games."""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from splitedge_importer.models import NormalizedGame, RejectedRecord
from splitedge_importer.validation.records import parse_id, parse_positive_id, trim

REGULAR_SEASON_PREFIX = "002"


def normalize_game_id(value: Any) -> str | None:
    if value is None:
        return None
    raw = trim(value)
    if not raw:
        return None
    if raw.isdigit():
        raw = raw.zfill(10)
    return raw


def is_regular_season_game_id(game_id: str) -> bool:
    return game_id.startswith(REGULAR_SEASON_PREFIX)


def normalize_games(
    rows: list[dict[str, Any]],
    *,
    season: str,
    known_team_ids: set[int],
) -> tuple[list[NormalizedGame], list[RejectedRecord], int, bool]:
    rejected: list[RejectedRecord] = []
    skipped_non_regular = 0
    by_game: dict[str, list[dict[str, Any]]] = {}
    seen_pairs: set[tuple[str, int]] = set()
    duplicate_ids = False

    for raw in rows:
        game_id = normalize_game_id(raw.get("GAME_ID"))
        team_id = parse_positive_id(raw.get("TEAM_ID"))
        if game_id is None:
            rejected.append(RejectedRecord(reason="missing_id", entity="game", raw=dict(raw)))
            continue
        if not is_regular_season_game_id(game_id):
            skipped_non_regular += 1
            continue
        if team_id is None:
            rejected.append(RejectedRecord(reason="missing_team", entity="game", raw=dict(raw)))
            continue
        pair = (game_id, team_id)
        if pair in seen_pairs:
            duplicate_ids = True
            rejected.append(RejectedRecord(reason="duplicate_id", entity="game", raw=dict(raw)))
            continue
        seen_pairs.add(pair)
        if team_id not in known_team_ids:
            rejected.append(RejectedRecord(reason="unknown_team", entity="game", raw=dict(raw)))
            continue
        by_game.setdefault(game_id, []).append(raw)

    games: list[NormalizedGame] = []
    for game_id, game_rows in by_game.items():
        if len(game_rows) != 2:
            rejected.append(
                RejectedRecord(
                    reason="unpaired_game",
                    entity="game",
                    raw={"GAME_ID": game_id, "row_count": len(game_rows)},
                )
            )
            continue
        paired = _pair_home_away(game_id, season, game_rows, known_team_ids)
        if isinstance(paired, RejectedRecord):
            rejected.append(paired)
            continue
        games.append(paired)

    return games, rejected, skipped_non_regular, duplicate_ids


def _pair_home_away(
    game_id: str,
    season: str,
    rows: list[dict[str, Any]],
    known_team_ids: set[int],
) -> NormalizedGame | RejectedRecord:
    home: dict[str, Any] | None = None
    away: dict[str, Any] | None = None
    for raw in rows:
        location = _location(raw.get("MATCHUP"))
        if location == "home":
            home = raw
        elif location == "away":
            away = raw
        else:
            return RejectedRecord(reason="invalid_matchup", entity="game", raw=dict(raw))
    if home is None or away is None:
        return RejectedRecord(
            reason="unpaired_game",
            entity="game",
            raw={"GAME_ID": game_id},
        )
    home_team = parse_positive_id(home.get("TEAM_ID"))
    away_team = parse_positive_id(away.get("TEAM_ID"))
    home_score = parse_id(home.get("PTS"))
    away_score = parse_id(away.get("PTS"))
    game_date = _parse_game_date(home.get("GAME_DATE") or away.get("GAME_DATE"))
    home_wl = trim(home.get("WL")).upper()
    away_wl = trim(away.get("WL")).upper()
    if home_team is None or away_team is None:
        return RejectedRecord(reason="missing_team", entity="game", raw={"GAME_ID": game_id})
    if home_team not in known_team_ids or away_team not in known_team_ids:
        return RejectedRecord(reason="unknown_team", entity="game", raw={"GAME_ID": game_id})
    if home_team == away_team:
        return RejectedRecord(reason="same_teams", entity="game", raw={"GAME_ID": game_id})
    if home_score is None or away_score is None or home_score < 0 or away_score < 0:
        return RejectedRecord(reason="missing_score", entity="game", raw={"GAME_ID": game_id})
    if home_wl not in {"W", "L"} or away_wl not in {"W", "L"}:
        return RejectedRecord(reason="incomplete_game", entity="game", raw={"GAME_ID": game_id})
    if game_date is None:
        return RejectedRecord(reason="missing_date", entity="game", raw={"GAME_ID": game_id})
    return NormalizedGame(
        nba_game_id=game_id,
        season=season,
        game_date=game_date,
        home_nba_team_id=home_team,
        away_nba_team_id=away_team,
        home_score=home_score,
        away_score=away_score,
    )


def _location(matchup: Any) -> str | None:
    text = trim(matchup)
    if " vs. " in text or " vs " in text.lower():
        return "home"
    if " @ " in text:
        return "away"
    return None


def _parse_game_date(value: Any) -> date | None:
    if value is None or value == "":
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    text = trim(value)
    if not text:
        return None
    try:
        return date.fromisoformat(text[:10])
    except ValueError:
        return None
