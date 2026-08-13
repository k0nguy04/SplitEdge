"""Insert historical player stubs without modifying existing roster rows."""

from __future__ import annotations

from psycopg import Connection

from splitedge_importer.models import HistoricalPlayerStub

INSERT_SQL = """
INSERT INTO players (
    nba_player_id,
    first_name,
    last_name,
    full_name,
    is_active,
    nba_team_id
)
VALUES (%s, %s, %s, %s, false, NULL)
ON CONFLICT (nba_player_id) DO NOTHING
"""


def insert_historical_stubs(conn: Connection, stubs: list[HistoricalPlayerStub]) -> int:
    if not stubs:
        return 0
    ids = [stub.nba_player_id for stub in stubs]
    existing = {
        int(row[0])
        for row in conn.execute(
            "SELECT nba_player_id FROM players WHERE nba_player_id = ANY(%s)",
            (ids,),
        ).fetchall()
    }
    to_insert = [stub for stub in stubs if stub.nba_player_id not in existing]
    if not to_insert:
        return 0
    with conn.cursor() as cursor:
        cursor.executemany(
            INSERT_SQL,
            [
                (stub.nba_player_id, stub.first_name, stub.last_name, stub.full_name)
                for stub in to_insert
            ],
        )
    return len(to_insert)
