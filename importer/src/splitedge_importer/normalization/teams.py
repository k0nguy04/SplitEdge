"""Normalize nba_api static team payloads."""

from __future__ import annotations

from typing import Any

from splitedge_importer.models import NormalizedTeam, RejectedRecord
from splitedge_importer.validation.records import parse_positive_id, trim


def normalize_teams(
    rows: list[dict[str, Any]],
) -> tuple[list[NormalizedTeam], list[RejectedRecord], bool]:
    valid: list[NormalizedTeam] = []
    rejected: list[RejectedRecord] = []
    seen: set[int] = set()
    duplicate_ids = False

    for raw in rows:
        team_id = parse_positive_id(raw.get("id"))
        full_name = trim(raw.get("full_name"))
        abbreviation = trim(raw.get("abbreviation"))
        nickname = trim(raw.get("nickname"))
        city = trim(raw.get("city"))
        if team_id is None:
            rejected.append(RejectedRecord(reason="missing_id", entity="team", raw=dict(raw)))
            continue
        if not full_name:
            rejected.append(RejectedRecord(reason="missing_name", entity="team", raw=dict(raw)))
            continue
        if not abbreviation or not nickname or not city:
            rejected.append(
                RejectedRecord(reason="missing_required_field", entity="team", raw=dict(raw))
            )
            continue
        if team_id in seen:
            duplicate_ids = True
            rejected.append(RejectedRecord(reason="duplicate_id", entity="team", raw=dict(raw)))
            continue
        seen.add(team_id)
        valid.append(
            NormalizedTeam(
                nba_team_id=team_id,
                abbreviation=abbreviation,
                full_name=full_name,
                nickname=nickname,
                city=city,
            )
        )

    return valid, rejected, duplicate_ids
