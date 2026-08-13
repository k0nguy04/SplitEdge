"""Team upserts. Callers control the surrounding transaction."""

from __future__ import annotations

from psycopg import Connection

from splitedge_importer.models import NormalizedTeam

UPSERT_SQL = """
INSERT INTO teams (
    nba_team_id,
    abbreviation,
    full_name,
    nickname,
    city
)
VALUES (%s, %s, %s, %s, %s)
ON CONFLICT (nba_team_id) DO UPDATE
SET abbreviation = EXCLUDED.abbreviation,
    full_name = EXCLUDED.full_name,
    nickname = EXCLUDED.nickname,
    city = EXCLUDED.city,
    updated_at = NOW()
"""


def upsert_teams(conn: Connection, teams: list[NormalizedTeam]) -> int:
    if not teams:
        return 0
    with conn.cursor() as cursor:
        cursor.executemany(
            UPSERT_SQL,
            [
                (team.nba_team_id, team.abbreviation, team.full_name, team.nickname, team.city)
                for team in teams
            ],
        )
    return len(teams)
