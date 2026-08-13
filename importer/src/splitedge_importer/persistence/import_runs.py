"""import_runs persistence. Callers control the surrounding transaction."""

from __future__ import annotations

from typing import Any

from psycopg import Connection
from psycopg.types.json import Jsonb


def insert_running(conn: Connection, import_type: str = "TEAMS_PLAYERS") -> int:
    row = conn.execute(
        """
        INSERT INTO import_runs (
            started_at,
            status,
            records_processed,
            records_failed,
            import_type
        )
        VALUES (NOW(), 'RUNNING', 0, 0, %s)
        RETURNING id
        """,
        (import_type,),
    ).fetchone()
    if row is None:
        raise RuntimeError("failed to insert import_runs RUNNING record")
    return int(row[0])


def update_completed(
    conn: Connection,
    run_id: int,
    *,
    records_processed: int,
    records_failed: int,
    details: dict[str, Any],
) -> None:
    conn.execute(
        """
        UPDATE import_runs
        SET status = 'COMPLETED',
            completed_at = NOW(),
            records_processed = %s,
            records_failed = %s,
            error_message = NULL,
            details = %s
        WHERE id = %s
        """,
        (records_processed, records_failed, Jsonb(details), run_id),
    )


def update_failed(
    conn: Connection,
    run_id: int,
    *,
    records_processed: int,
    records_failed: int,
    error_message: str | None,
    details: dict[str, Any],
) -> None:
    conn.execute(
        """
        UPDATE import_runs
        SET status = 'FAILED',
            completed_at = NOW(),
            records_processed = %s,
            records_failed = %s,
            error_message = %s,
            details = %s
        WHERE id = %s
        """,
        (records_processed, records_failed, error_message, Jsonb(details), run_id),
    )
