"""Player upserts and guarded deactivation. Callers control the transaction."""

from __future__ import annotations

from psycopg import Connection

from splitedge_importer.models import NormalizedPlayer

UPSERT_SQL = """
INSERT INTO players (
    nba_player_id,
    first_name,
    last_name,
    full_name,
    is_active,
    nba_team_id
)
VALUES (%s, %s, %s, %s, %s, %s)
ON CONFLICT (nba_player_id) DO UPDATE
SET first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    full_name = EXCLUDED.full_name,
    is_active = EXCLUDED.is_active,
    nba_team_id = EXCLUDED.nba_team_id,
    updated_at = NOW()
"""

DEACTIVATE_SQL = """
UPDATE players
SET is_active = false,
    updated_at = NOW()
WHERE is_active = true
  AND NOT (nba_player_id = ANY(%s))
RETURNING nba_player_id
"""


def upsert_players(conn: Connection, players: list[NormalizedPlayer]) -> int:
    if not players:
        return 0
    with conn.cursor() as cursor:
        cursor.executemany(
            UPSERT_SQL,
            [
                (
                    player.nba_player_id,
                    player.first_name,
                    player.last_name,
                    player.full_name,
                    player.is_active,
                    player.nba_team_id,
                )
                for player in players
            ],
        )
    return len(players)


def deactivate_missing_players(conn: Connection, active_player_ids: list[int]) -> int:
    if not active_player_ids:
        raise RuntimeError("refusing player deactivation because the active player id set is empty")
    rows = conn.execute(DEACTIVATE_SQL, (active_player_ids,)).fetchall()
    return len(rows)
