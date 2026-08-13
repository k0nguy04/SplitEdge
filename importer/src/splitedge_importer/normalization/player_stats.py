"""Normalize LeagueGameLog player-mode rows into box-score persist candidates."""

from __future__ import annotations

from typing import Any

from splitedge_importer.models import (
    HistoricalPlayerStub,
    NormalizedGame,
    NormalizedPlayerGameStat,
    RejectedRecord,
)
from splitedge_importer.normalization.games import is_regular_season_game_id, normalize_game_id
from splitedge_importer.normalization.minutes import parse_minutes
from splitedge_importer.validation.records import parse_id, parse_positive_id, trim

MAX_STUB_NAME_LENGTH = 64


def normalize_player_stats(
    rows: list[dict[str, Any]],
    *,
    season: str,
    games: list[NormalizedGame],
    known_team_ids: set[int],
) -> tuple[
    list[NormalizedPlayerGameStat],
    list[RejectedRecord],
    int,
    int,
    bool,
]:
    games_by_id = {game.nba_game_id: game for game in games if game.season == season}
    valid: list[NormalizedPlayerGameStat] = []
    rejected: list[RejectedRecord] = []
    skipped_dnp = 0
    skipped_non_regular = 0
    seen: set[tuple[int, str]] = set()
    duplicate_ids = False

    for raw in rows:
        game_id = normalize_game_id(raw.get("GAME_ID"))
        player_id = parse_positive_id(raw.get("PLAYER_ID"))
        if player_id is None:
            rejected.append(_reject("missing_id", raw, season))
            continue
        if game_id is None:
            rejected.append(_reject("missing_game", raw, season))
            continue
        if not is_regular_season_game_id(game_id):
            skipped_non_regular += 1
            continue
        pair = (player_id, game_id)
        if pair in seen:
            duplicate_ids = True
            rejected.append(_reject("duplicate_id", raw, season, game_id))
            continue
        seen.add(pair)

        minutes, minutes_reason = parse_minutes(raw.get("MIN"))
        if minutes_reason == "skipped_dnp":
            skipped_dnp += 1
            continue
        if minutes is None:
            rejected.append(_reject("malformed_minutes", raw, season, game_id))
            continue

        game = games_by_id.get(game_id)
        if game is None:
            rejected.append(_reject("unknown_game", raw, season, game_id))
            continue

        team_id = parse_positive_id(raw.get("TEAM_ID"))
        if team_id is None:
            rejected.append(_reject("missing_team", raw, season, game_id))
            continue
        if team_id not in known_team_ids:
            rejected.append(_reject("unknown_team", raw, season, game_id))
            continue
        if team_id not in {game.home_nba_team_id, game.away_nba_team_id}:
            rejected.append(_reject("team_not_in_game", raw, season, game_id))
            continue

        points = parse_id(raw.get("PTS"))
        rebounds = parse_id(raw.get("REB"))
        assists = parse_id(raw.get("AST"))
        threes = parse_id(raw.get("FG3M"))
        if None in {points, rebounds, assists, threes}:
            rejected.append(_reject("missing_stat", raw, season, game_id))
            continue
        assert points is not None and rebounds is not None
        assert assists is not None and threes is not None
        if min(points, rebounds, assists, threes) < 0:
            rejected.append(_reject("negative_stat", raw, season, game_id))
            continue

        player_name = trim(raw.get("PLAYER_NAME"))
        if not player_name:
            rejected.append(_reject("missing_name", raw, season, game_id))
            continue
        if len(player_name) > MAX_STUB_NAME_LENGTH:
            rejected.append(_reject("name_too_long", raw, season, game_id))
            continue

        valid.append(
            NormalizedPlayerGameStat(
                nba_game_id=game_id,
                season=season,
                nba_player_id=player_id,
                nba_team_id=team_id,
                minutes=minutes,
                points=points,
                rebounds=rebounds,
                assists=assists,
                three_pointers_made=threes,
                player_name=player_name,
            )
        )

    return valid, rejected, skipped_dnp, skipped_non_regular, duplicate_ids


def _reject(
    reason: str,
    raw: dict[str, Any],
    season: str,
    nba_game_id: str | None = None,
) -> RejectedRecord:
    return RejectedRecord(
        reason=reason,
        entity="player_stat",
        raw=dict(raw),
        season=season,
        nba_game_id=nba_game_id or normalize_game_id(raw.get("GAME_ID")),
    )


def stubs_for_unknown_players(
    stats: list[NormalizedPlayerGameStat],
    known_player_ids: set[int],
) -> list[HistoricalPlayerStub]:
    stubs: list[HistoricalPlayerStub] = []
    seen: set[int] = set()
    for stat in stats:
        if stat.nba_player_id in known_player_ids or stat.nba_player_id in seen:
            continue
        seen.add(stat.nba_player_id)
        stubs.append(
            HistoricalPlayerStub(
                nba_player_id=stat.nba_player_id,
                first_name=stat.player_name,
                last_name=stat.player_name,
                full_name=stat.player_name,
            )
        )
    return stubs
