package dev.splitedge.importstatus;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Fixture rows scoped to this class's own disjoint IDs for games/players/teams/stats.
 * {@code import_runs} has no natural per-row scope (freshness is a table-wide MAX), so
 * it is fully owned (wiped, then restored to a known baseline) for the lifetime of the
 * owning test class, matching the precedent already used by the report module's ITs.
 */
final class DataStatusServiceItFixtures {

    static final long TEAM_A = 9_000_000_041L;
    static final long TEAM_B = 9_000_000_042L;

    static final long ACTIVE_PLAYER_1 = 9_000_004_001L;
    static final long ACTIVE_PLAYER_2 = 9_000_004_002L;
    static final long INACTIVE_PLAYER = 9_000_004_003L;

    static final String GAME_1 = "0029903001";
    static final String GAME_2 = "0029903002";

    static final long BASELINE_RUN_RECORDS_PROCESSED = 42L;
    static final long BASELINE_RUN_RECORDS_FAILED = 1L;
    static final String BASELINE_RUN_COMPLETED_AT = "2025-06-01T00:00:00Z";

    private DataStatusServiceItFixtures() {}

    static void deleteScopedRows(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM player_game_stats WHERE nba_player_id IN (9000004001, 9000004002, 9000004003)")
                .update();
        jdbc.sql("DELETE FROM games WHERE nba_game_id IN ('0029903001', '0029903002')").update();
        jdbc.sql("DELETE FROM players WHERE nba_player_id IN (9000004001, 9000004002, 9000004003)")
                .update();
        jdbc.sql("DELETE FROM teams WHERE nba_team_id IN (9000000041, 9000000042)").update();
    }

    static void insertScopedRows(JdbcClient jdbc) {
        insertTeam(jdbc, TEAM_A, "SDA", "Status City", "Adders");
        insertTeam(jdbc, TEAM_B, "SDB", "Status Town", "Blazers");

        insertPlayer(jdbc, ACTIVE_PLAYER_1, "Status", "Active1", TEAM_A, true);
        insertPlayer(jdbc, ACTIVE_PLAYER_2, "Status", "Active2", TEAM_B, true);
        insertPlayer(jdbc, INACTIVE_PLAYER, "Status", "Retired", TEAM_A, false);

        insertGame(jdbc, GAME_1, "2024-25", "2024-11-05", TEAM_A, TEAM_B);
        insertGame(jdbc, GAME_2, "2024-25", "2024-11-07", TEAM_B, TEAM_A);

        insertStats(jdbc, GAME_1, ACTIVE_PLAYER_1, TEAM_A);
        insertStats(jdbc, GAME_1, ACTIVE_PLAYER_2, TEAM_B);
        insertStats(jdbc, GAME_2, INACTIVE_PLAYER, TEAM_A);
    }

    static void wipeImportRuns(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM import_runs").update();
    }

    static void insertBaselineCompletedRun(JdbcClient jdbc) {
        insertImportRun(
                jdbc,
                "GAMES_STATS",
                "COMPLETED",
                BASELINE_RUN_COMPLETED_AT,
                BASELINE_RUN_RECORDS_PROCESSED,
                BASELINE_RUN_RECORDS_FAILED);
    }

    static void insertImportRun(
            JdbcClient jdbc, String importType, String status, String completedAt, long processed, long failed) {
        jdbc.sql("""
                        INSERT INTO import_runs (
                            started_at, completed_at, status, import_type, records_processed, records_failed)
                        VALUES (NOW(), CAST(:completedAt AS TIMESTAMPTZ), :status, :importType, :processed, :failed)
                        """)
                .param("completedAt", completedAt)
                .param("status", status)
                .param("importType", importType)
                .param("processed", processed)
                .param("failed", failed)
                .update();
    }

    private static void insertTeam(JdbcClient jdbc, long nbaTeamId, String abbreviation, String city, String nickname) {
        jdbc.sql("""
                        INSERT INTO teams (nba_team_id, abbreviation, full_name, nickname, city)
                        VALUES (:id, :abbr, :fullName, :nickname, :city)
                        """)
                .param("id", nbaTeamId)
                .param("abbr", abbreviation)
                .param("fullName", city + " " + nickname)
                .param("nickname", nickname)
                .param("city", city)
                .update();
    }

    private static void insertPlayer(
            JdbcClient jdbc, long nbaPlayerId, String first, String last, long rosterTeam, boolean active) {
        jdbc.sql("""
                        INSERT INTO players (
                            nba_player_id, first_name, last_name, full_name, is_active, nba_team_id)
                        VALUES (:id, :first, :last, :fullName, :active, :team)
                        """)
                .param("id", nbaPlayerId)
                .param("first", first)
                .param("last", last)
                .param("fullName", first + " " + last)
                .param("active", active)
                .param("team", rosterTeam)
                .update();
    }

    private static void insertGame(
            JdbcClient jdbc, String nbaGameId, String season, String gameDate, long home, long away) {
        jdbc.sql("""
                        INSERT INTO games (
                            nba_game_id, season, game_date, home_nba_team_id, away_nba_team_id,
                            home_score, away_score, status)
                        VALUES (:id, :season, CAST(:gameDate AS DATE), :home, :away, 100, 95, 'FINAL')
                        """)
                .param("id", nbaGameId)
                .param("season", season)
                .param("gameDate", gameDate)
                .param("home", home)
                .param("away", away)
                .update();
    }

    private static void insertStats(JdbcClient jdbc, String nbaGameId, long nbaPlayerId, long nbaTeamId) {
        jdbc.sql("""
                        INSERT INTO player_game_stats (
                            nba_game_id, nba_player_id, nba_team_id, minutes, points, rebounds,
                            assists, three_pointers_made)
                        VALUES (:game, :player, :team, CAST('30.000' AS NUMERIC), 20, 5, 4, 1)
                        """)
                .param("game", nbaGameId)
                .param("player", nbaPlayerId)
                .param("team", nbaTeamId)
                .update();
    }
}
