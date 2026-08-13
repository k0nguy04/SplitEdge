"""Game upserts. Callers control the surrounding transaction."""

from __future__ import annotations

from psycopg import Connection

from splitedge_importer.models import NormalizedGame

UPSERT_SQL = """
INSERT INTO games (
    nba_game_id,
    season,
    game_date,
    home_nba_team_id,
    away_nba_team_id,
    home_score,
    away_score,
    status
)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
ON CONFLICT (nba_game_id) DO UPDATE
SET season = EXCLUDED.season,
    game_date = EXCLUDED.game_date,
    home_nba_team_id = EXCLUDED.home_nba_team_id,
    away_nba_team_id = EXCLUDED.away_nba_team_id,
    home_score = EXCLUDED.home_score,
    away_score = EXCLUDED.away_score,
    status = EXCLUDED.status,
    updated_at = NOW()
"""


def upsert_games(conn: Connection, games: list[NormalizedGame]) -> int:
    if not games:
        return 0
    with conn.cursor() as cursor:
        cursor.executemany(
            UPSERT_SQL,
            [
                (
                    game.nba_game_id,
                    game.season,
                    game.game_date,
                    game.home_nba_team_id,
                    game.away_nba_team_id,
                    game.home_score,
                    game.away_score,
                    game.status,
                )
                for game in games
            ],
        )
    return len(games)
