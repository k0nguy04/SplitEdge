"""Normalize LeagueGameLog team-mode rows into completed regular-season games."""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from splitedge_importer.models import AmbiguousGame, NormalizedGame, RejectedRecord
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
) -> tuple[list[NormalizedGame], list[AmbiguousGame], list[RejectedRecord], int, bool]:
    rejected: list[RejectedRecord] = []
    skipped_non_regular = 0
    by_game: dict[str, list[dict[str, Any]]] = {}
    seen_pairs: set[tuple[str, int]] = set()
    duplicate_ids = False

    for raw in rows:
        game_id = normalize_game_id(raw.get("GAME_ID"))
        team_id = parse_positive_id(raw.get("TEAM_ID"))
        if game_id is None:
            rejected.append(_reject("missing_id", dict(raw), season, None))
            continue
        if not is_regular_season_game_id(game_id):
            skipped_non_regular += 1
            continue
        if team_id is None:
            rejected.append(_reject("missing_team", dict(raw), season, game_id))
            continue
        pair = (game_id, team_id)
        if pair in seen_pairs:
            duplicate_ids = True
            rejected.append(_reject("duplicate_id", dict(raw), season, game_id))
            continue
        seen_pairs.add(pair)
        if team_id not in known_team_ids:
            rejected.append(_reject("unknown_team", dict(raw), season, game_id))
            continue
        by_game.setdefault(game_id, []).append(raw)

    games: list[NormalizedGame] = []
    ambiguous: list[AmbiguousGame] = []
    for game_id, game_rows in by_game.items():
        if len(game_rows) != 2:
            rejected.append(
                _reject(
                    "unpaired_game",
                    {"GAME_ID": game_id, "rows": [_team_audit(item) for item in game_rows]},
                    season,
                    game_id,
                )
            )
            continue
        paired = _pair_home_away(game_id, season, game_rows, known_team_ids)
        if isinstance(paired, RejectedRecord):
            rejected.append(paired)
            continue
        if isinstance(paired, AmbiguousGame):
            ambiguous.append(paired)
            continue
        games.append(paired)

    return games, ambiguous, rejected, skipped_non_regular, duplicate_ids


def complete_ambiguous_game(
    ambiguous: AmbiguousGame,
    home_nba_team_id: int,
    away_nba_team_id: int,
) -> NormalizedGame:
    by_team: dict[int, dict[str, Any]] = {}
    for raw in ambiguous.team_rows:
        team_id = parse_positive_id(raw.get("TEAM_ID"))
        if team_id is not None:
            by_team[team_id] = raw
    home = by_team[home_nba_team_id]
    away = by_team[away_nba_team_id]
    home_score = parse_id(home.get("PTS"))
    away_score = parse_id(away.get("PTS"))
    assert home_score is not None and away_score is not None
    return NormalizedGame(
        nba_game_id=ambiguous.nba_game_id,
        season=ambiguous.season,
        game_date=ambiguous.game_date,
        home_nba_team_id=home_nba_team_id,
        away_nba_team_id=away_nba_team_id,
        home_score=home_score,
        away_score=away_score,
    )


def _pair_home_away(
    game_id: str,
    season: str,
    rows: list[dict[str, Any]],
    known_team_ids: set[int],
) -> NormalizedGame | AmbiguousGame | RejectedRecord:
    homes: list[dict[str, Any]] = []
    aways: list[dict[str, Any]] = []
    for raw in rows:
        location = _location(raw.get("MATCHUP"))
        if location == "home":
            homes.append(raw)
        elif location == "away":
            aways.append(raw)
        else:
            return _reject("invalid_matchup", dict(raw), season, game_id)

    completed = _completed_team_rows(game_id, season, rows, known_team_ids)
    if isinstance(completed, RejectedRecord):
        return completed
    game_date, _by_team = completed

    if len(homes) == 1 and len(aways) == 1:
        home = homes[0]
        away = aways[0]
        home_team = parse_positive_id(home.get("TEAM_ID"))
        away_team = parse_positive_id(away.get("TEAM_ID"))
        home_score = parse_id(home.get("PTS"))
        away_score = parse_id(away.get("PTS"))
        assert home_team is not None and away_team is not None
        assert home_score is not None and away_score is not None
        return NormalizedGame(
            nba_game_id=game_id,
            season=season,
            game_date=game_date,
            home_nba_team_id=home_team,
            away_nba_team_id=away_team,
            home_score=home_score,
            away_score=away_score,
        )
    if (len(homes) == 2 and not aways) or (len(aways) == 2 and not homes):
        return AmbiguousGame(
            nba_game_id=game_id,
            season=season,
            game_date=game_date,
            team_rows=(dict(rows[0]), dict(rows[1])),
        )
    return _reject(
        "unpaired_game",
        {"GAME_ID": game_id, "rows": [_team_audit(item) for item in rows]},
        season,
        game_id,
    )


def _completed_team_rows(
    game_id: str,
    season: str,
    rows: list[dict[str, Any]],
    known_team_ids: set[int],
) -> tuple[date, dict[int, dict[str, Any]]] | RejectedRecord:
    audits = [_team_audit(raw) for raw in rows]
    by_team: dict[int, dict[str, Any]] = {}
    game_date: date | None = None
    for raw in rows:
        team_id = parse_positive_id(raw.get("TEAM_ID"))
        score = parse_id(raw.get("PTS"))
        wl = trim(raw.get("WL")).upper()
        parsed_date = _parse_game_date(raw.get("GAME_DATE"))
        if team_id is None:
            return _reject("missing_team", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        if team_id not in known_team_ids:
            return _reject("unknown_team", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        if team_id in by_team:
            return _reject("same_teams", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        if score is None or score < 0:
            return _reject("missing_score", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        if wl not in {"W", "L"}:
            return _reject("incomplete_game", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        if parsed_date is None:
            return _reject("missing_date", {"GAME_ID": game_id, "rows": audits}, season, game_id)
        by_team[team_id] = raw
        if game_date is None:
            game_date = parsed_date
    if game_date is None or len(by_team) != 2:
        return _reject("unpaired_game", {"GAME_ID": game_id, "rows": audits}, season, game_id)
    return game_date, by_team


def _team_audit(raw: dict[str, Any]) -> dict[str, Any]:
    return {
        "GAME_ID": raw.get("GAME_ID"),
        "TEAM_ID": raw.get("TEAM_ID"),
        "MATCHUP": raw.get("MATCHUP"),
        "WL": raw.get("WL"),
        "PTS": raw.get("PTS"),
    }


def _reject(
    reason: str,
    raw: dict[str, Any],
    season: str,
    nba_game_id: str | None,
) -> RejectedRecord:
    game_id = nba_game_id if nba_game_id is not None else normalize_game_id(raw.get("GAME_ID"))
    return RejectedRecord(
        reason=reason,
        entity="game",
        raw=dict(raw),
        season=season,
        nba_game_id=game_id,
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
