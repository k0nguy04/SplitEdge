"""Player box-score upserts. Callers control the surrounding transaction."""

from __future__ import annotations

from psycopg import Connection

from splitedge_importer.models import NormalizedPlayerGameStat

UPSERT_SQL = """
INSERT INTO player_game_stats (
    nba_game_id,
    nba_player_id,
    nba_team_id,
    minutes,
    points,
    rebounds,
    assists,
    three_pointers_made
)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
ON CONFLICT (nba_player_id, nba_game_id) DO UPDATE
SET nba_team_id = EXCLUDED.nba_team_id,
    minutes = EXCLUDED.minutes,
    points = EXCLUDED.points,
    rebounds = EXCLUDED.rebounds,
    assists = EXCLUDED.assists,
    three_pointers_made = EXCLUDED.three_pointers_made,
    updated_at = NOW()
"""


def upsert_player_stats(conn: Connection, stats: list[NormalizedPlayerGameStat]) -> int:
    if not stats:
        return 0
    with conn.cursor() as cursor:
        cursor.executemany(
            UPSERT_SQL,
            [
                (
                    stat.nba_game_id,
                    stat.nba_player_id,
                    stat.nba_team_id,
                    stat.minutes,
                    stat.points,
                    stat.rebounds,
                    stat.assists,
                    stat.three_pointers_made,
                )
                for stat in stats
            ],
        )
    return len(stats)
