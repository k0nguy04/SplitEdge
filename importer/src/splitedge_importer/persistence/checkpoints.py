"""Checkpoint load/save and FETCHED payload reuse rules."""

from __future__ import annotations

from typing import Any

from psycopg import Connection
from psycopg.types.json import Jsonb

from splitedge_importer.models import CheckpointRecord
from splitedge_importer.redact import json_safe
from splitedge_importer.retrieval.box_score_summary import (
    REFETCH_SUMMARY_REASONS,
    HomeAwayResolverError,
    is_summary_resource,
    validate_game_summary,
)
from splitedge_importer.retrieval.league_game_log import REQUIRED_COLUMNS, required_columns_for


def load_checkpoint(
    conn: Connection,
    import_type: str,
    season: str,
    resource: str,
) -> CheckpointRecord | None:
    row = conn.execute(
        """
        SELECT import_type, season, resource, status, row_count, payload
        FROM import_checkpoints
        WHERE import_type = %s AND season = %s AND resource = %s
        """,
        (import_type, season, resource),
    ).fetchone()
    if row is None:
        return None
    return CheckpointRecord(
        import_type=row[0],
        season=row[1],
        resource=row[2],
        status=row[3],
        row_count=int(row[4]),
        payload=row[5],
    )


def save_fetched_checkpoint(
    conn: Connection,
    *,
    import_type: str,
    season: str,
    resource: str,
    rows: list[dict[str, Any]] | None = None,
    payload: Any = None,
) -> None:
    body = payload if payload is not None else rows
    if body is None:
        raise ValueError("checkpoint payload is required")
    row_count = len(body) if isinstance(body, list) else 1
    conn.execute(
        """
        INSERT INTO import_checkpoints (
            import_type, season, resource, status, row_count, payload, updated_at
        )
        VALUES (%s, %s, %s, 'FETCHED', %s, %s, NOW())
        ON CONFLICT (import_type, season, resource) DO UPDATE
        SET status = 'FETCHED',
            row_count = EXCLUDED.row_count,
            payload = EXCLUDED.payload,
            updated_at = NOW()
        """,
        (import_type, season, resource, row_count, Jsonb(json_safe(body))),
    )


def mark_checkpoints_persisted(
    conn: Connection,
    *,
    import_type: str,
    used_checkpoints: tuple[tuple[str, str], ...],
) -> None:
    for season, resource in used_checkpoints:
        conn.execute(
            """
            UPDATE import_checkpoints
            SET status = 'PERSISTED',
                payload = NULL,
                updated_at = NOW()
            WHERE import_type = %s
              AND season = %s
              AND resource = %s
            """,
            (import_type, season, resource),
        )


def reusable_fetched_rows(
    checkpoint: CheckpointRecord | None,
    *,
    import_type: str,
    season: str,
    resource: str,
) -> list[dict[str, Any]] | None:
    """Return rows only for a structurally valid FETCHED checkpoint."""
    if checkpoint is None:
        return None
    if checkpoint.status != "FETCHED":
        return None
    if checkpoint.import_type != import_type:
        return None
    if checkpoint.season != season:
        return None
    if checkpoint.resource != resource:
        return None
    if resource not in REQUIRED_COLUMNS or is_summary_resource(resource):
        return None
    rows = _payload_as_rows(checkpoint.payload)
    if not rows:
        return None
    required = required_columns_for(resource)
    for row in rows:
        if not required.issubset(row.keys()):
            return None
    return rows


def reusable_fetched_summary(
    checkpoint: CheckpointRecord | None,
    *,
    import_type: str,
    season: str,
    resource: str,
    requested_game_id: str,
    log_team_ids: set[int],
) -> dict[str, Any] | None:
    """Return a validated GameSummary dict only for a reusable FETCHED checkpoint."""
    if checkpoint is None:
        return None
    if checkpoint.status != "FETCHED":
        return None
    if checkpoint.import_type != import_type:
        return None
    if checkpoint.season != season:
        return None
    if checkpoint.resource != resource:
        return None
    payload = checkpoint.payload
    if not isinstance(payload, dict) or not payload:
        return None
    try:
        return validate_game_summary(
            payload,
            requested_game_id=requested_game_id,
            log_team_ids=log_team_ids,
            season=season,
        )
    except HomeAwayResolverError as exc:
        if exc.reason in REFETCH_SUMMARY_REASONS:
            return None
        raise


def _payload_as_rows(payload: Any) -> list[dict[str, Any]] | None:
    if isinstance(payload, list):
        if not payload or not all(isinstance(item, dict) for item in payload):
            return None
        return [dict(item) for item in payload]
    if isinstance(payload, dict):
        headers = payload.get("headers")
        row_set = payload.get("rowSet")
        if not isinstance(headers, list) or not isinstance(row_set, list) or not row_set:
            return None
        return [dict(zip(headers, row, strict=False)) for row in row_set]
    return None
