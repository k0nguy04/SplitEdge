"""Normalize CommonAllPlayers rows into active-player records."""

from __future__ import annotations

from typing import Any

from splitedge_importer.models import NormalizedPlayer, RejectedRecord, WarningRecord
from splitedge_importer.validation.records import (
    is_active_roster_status,
    parse_id,
    parse_positive_id,
    trim,
)


def normalize_players(
    rows: list[dict[str, Any]],
    known_team_ids: set[int],
) -> tuple[list[NormalizedPlayer], list[RejectedRecord], list[WarningRecord], int, bool]:
    valid: list[NormalizedPlayer] = []
    rejected: list[RejectedRecord] = []
    warnings: list[WarningRecord] = []
    seen: set[int] = set()
    skipped_inactive = 0
    duplicate_ids = False

    for raw in rows:
        player_id = parse_positive_id(raw.get("PERSON_ID"))
        if player_id is None:
            rejected.append(RejectedRecord(reason="missing_id", entity="player", raw=dict(raw)))
            continue
        if player_id in seen:
            duplicate_ids = True
            rejected.append(RejectedRecord(reason="duplicate_id", entity="player", raw=dict(raw)))
            continue
        seen.add(player_id)

        if not is_active_roster_status(raw.get("ROSTERSTATUS")):
            skipped_inactive += 1
            continue

        full_name = trim(raw.get("DISPLAY_FIRST_LAST"))
        if not full_name:
            rejected.append(RejectedRecord(reason="missing_name", entity="player", raw=dict(raw)))
            continue

        first_name, last_name = _split_name(full_name)
        team_id, missing_team, warning_reason = _resolve_team(raw.get("TEAM_ID"), known_team_ids)
        player = NormalizedPlayer(
            nba_player_id=player_id,
            first_name=first_name,
            last_name=last_name,
            full_name=full_name,
            is_active=True,
            nba_team_id=team_id,
            missing_team=missing_team,
        )
        valid.append(player)
        if missing_team:
            warnings.append(
                WarningRecord(
                    reason=warning_reason or "missing_team",
                    entity="player",
                    nba_player_id=player_id,
                    raw=dict(raw),
                )
            )

    return valid, rejected, warnings, skipped_inactive, duplicate_ids


def _split_name(full_name: str) -> tuple[str, str]:
    parts = full_name.split(None, 1)
    if len(parts) == 1:
        return parts[0], parts[0]
    return parts[0], parts[1]


def _resolve_team(
    raw_team_id: Any, known_team_ids: set[int]
) -> tuple[int | None, bool, str | None]:
    team_id = parse_id(raw_team_id)
    if team_id is None or team_id == 0:
        return None, True, "missing_team"
    if team_id not in known_team_ids:
        return None, True, "unknown_team"
    return team_id, False, None
