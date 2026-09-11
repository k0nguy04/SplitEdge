package dev.splitedge.report;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;

final class MatchupReportItFixtures {

    static final long PLAYER = 9_000_002_011L;
    static final long TEAM = 9_000_000_011L;
    static final long OPPONENT = 9_000_000_012L;
    static final long OTHER_TEAM = 9_000_000_013L;

    static final String VS_OPPONENT_HOME = "0029901001";
    static final String VS_OPPONENT_AWAY = "0029901002";
    static final String VS_OTHER_TEAM = "0029901003";
    static final String PRIOR_SEASON = "0029901004";
    static final String VS_OPPONENT_LATE = "0029901005";

    static final Instant LATEST_COMPLETED = Instant.parse("2025-03-02T08:15:30Z");

    private MatchupReportItFixtures() {}

    static void deleteAll(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM player_game_stats WHERE nba_player_id = " + PLAYER).update();
        jdbc.sql("""
                        DELETE FROM games
                        WHERE nba_game_id IN (
                            '0029901001', '0029901002', '0029901003', '0029901004', '0029901005')
                        """)
                .update();
        jdbc.sql("DELETE FROM players WHERE nba_player_id = " + PLAYER).update();
        jdbc.sql("DELETE FROM teams WHERE nba_team_id IN (9000000011, 9000000012, 9000000013)")
                .update();
        // Freshness is a table-wide MAX, so the dedicated test database owns every row.
        jdbc.sql("DELETE FROM import_runs").update();
    }

    static void insertAll(JdbcClient jdbc) {
        insertTeam(jdbc, TEAM, "RPT", "Report Team");
        insertTeam(jdbc, OPPONENT, "ROP", "Report Opponent");
        insertTeam(jdbc, OTHER_TEAM, "ROT", "Report Other");
        insertPlayer(jdbc);

        insertGame(jdbc, VS_OPPONENT_HOME, "2024-25", "2024-10-22", TEAM, OPPONENT);
        insertGame(jdbc, VS_OPPONENT_AWAY, "2024-25", "2024-11-01", OPPONENT, TEAM);
        insertGame(jdbc, VS_OTHER_TEAM, "2024-25", "2024-11-10", TEAM, OTHER_TEAM);
        insertGame(jdbc, PRIOR_SEASON, "2023-24", "2023-12-01", TEAM, OPPONENT);
        insertGame(jdbc, VS_OPPONENT_LATE, "2024-25", "2024-12-01", TEAM, OPPONENT);

        insertStats(jdbc, VS_OPPONENT_HOME, "36.000", 30, 5, 8, 4);
        insertStats(jdbc, VS_OPPONENT_AWAY, "32.000", 20, 4, 6, 2);
        insertStats(jdbc, VS_OTHER_TEAM, "34.000", 27, 5, 9, 5);
        insertStats(jdbc, PRIOR_SEASON, "30.000", 40, 10, 10, 6);
        insertStats(jdbc, VS_OPPONENT_LATE, "40.000", 24, 8, 6, 2);

        insertImportRun(jdbc, "GAMES_STATS", "COMPLETED", "2025-03-01T10:00:00Z");
        insertImportRun(jdbc, "GAMES_STATS", "COMPLETED", LATEST_COMPLETED.toString());
        insertImportRun(jdbc, "GAMES_STATS", "RUNNING", null);
        insertImportRun(jdbc, "GAMES_STATS", "FAILED", "2025-04-01T00:00:00Z");
        insertImportRun(jdbc, "TEAMS_PLAYERS", "COMPLETED", "2025-05-01T00:00:00Z");
    }

    static void insertImportRun(JdbcClient jdbc, String importType, String status, String completedAt) {
        jdbc.sql("""
                        INSERT INTO import_runs (started_at, completed_at, status, import_type)
                        VALUES (NOW(), CAST(:completedAt AS TIMESTAMPTZ), :status, :importType)
                        """)
                .param("completedAt", completedAt)
                .param("status", status)
                .param("importType", importType)
                .update();
    }

    private static void insertTeam(JdbcClient jdbc, long nbaTeamId, String abbreviation, String fullName) {
        jdbc.sql("""
                        INSERT INTO teams (nba_team_id, abbreviation, full_name, nickname, city)
                        VALUES (:id, :abbr, :fullName, :nickname, :city)
                        """)
                .param("id", nbaTeamId)
                .param("abbr", abbreviation)
                .param("fullName", fullName)
                .param("nickname", abbreviation)
                .param("city", "Report City")
                .update();
    }

    private static void insertPlayer(JdbcClient jdbc) {
        jdbc.sql("""
                        INSERT INTO players (
                            nba_player_id, first_name, last_name, full_name, is_active, nba_team_id)
                        VALUES (:id, 'Report', 'Player', 'Report Player', TRUE, :team)
                        """)
                .param("id", PLAYER)
                .param("team", TEAM)
                .update();
    }

    private static void insertGame(
            JdbcClient jdbc, String nbaGameId, String season, String gameDate, long home, long away) {
        jdbc.sql("""
                        INSERT INTO games (
                            nba_game_id, season, game_date, home_nba_team_id, away_nba_team_id,
                            home_score, away_score, status)
                        VALUES (:id, :season, CAST(:gameDate AS DATE), :home, :away, 110, 105, 'FINAL')
                        """)
                .param("id", nbaGameId)
                .param("season", season)
                .param("gameDate", gameDate)
                .param("home", home)
                .param("away", away)
                .update();
    }

    private static void insertStats(
            JdbcClient jdbc,
            String nbaGameId,
            String minutes,
            int points,
            int rebounds,
            int assists,
            int threes) {
        jdbc.sql("""
                        INSERT INTO player_game_stats (
                            nba_game_id, nba_player_id, nba_team_id, minutes, points, rebounds,
                            assists, three_pointers_made)
                        VALUES (:game, :player, :team, CAST(:minutes AS NUMERIC), :points, :rebounds,
                                :assists, :threes)
                        """)
                .param("game", nbaGameId)
                .param("player", PLAYER)
                .param("team", TEAM)
                .param("minutes", minutes)
                .param("points", points)
                .param("rebounds", rebounds)
                .param("assists", assists)
                .param("threes", threes)
                .update();
    }
}
