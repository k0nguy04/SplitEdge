package dev.splitedge.report;

import org.springframework.jdbc.core.simple.JdbcClient;

final class PlayerGameLineRepositoryFixtures {

    static final long HOME_TEAM = 9_000_000_001L;
    static final long AWAY_TEAM = 9_000_000_002L;
    static final long ROSTER_TEAM = 9_000_000_003L;

    static final long SUBJECT_PLAYER = 9_000_001_001L;
    static final long OTHER_PLAYER = 9_000_001_002L;
    static final long INCONSISTENT_PLAYER = 9_000_001_003L;

    static final String HOME_GAME = "0029900001";
    static final String AWAY_GAME = "0029900002";
    static final String OTHER_OPPONENT_GAME = "0029900003";
    static final String PRIOR_SEASON_GAME = "0029900004";
    static final String TIE_BREAK_LOW = "0029900005";
    static final String TIE_BREAK_HIGH = "0029900006";
    static final String OTHER_PLAYER_GAME = "0029900007";
    static final String INCONSISTENT_GAME = "0029900088";
    static final String NON_FINAL_GAME = "0029900099";

    private PlayerGameLineRepositoryFixtures() {}

    static void deleteAll(JdbcClient jdbc) {
        jdbc.sql("""
                        DELETE FROM player_game_stats
                        WHERE nba_player_id IN (9000001001, 9000001002, 9000001003)
                           OR nba_game_id IN (
                                '0029900001', '0029900002', '0029900003', '0029900004',
                                '0029900005', '0029900006', '0029900007', '0029900088', '0029900099')
                        """)
                .update();
        jdbc.sql("""
                        DELETE FROM games
                        WHERE nba_game_id IN (
                            '0029900001', '0029900002', '0029900003', '0029900004',
                            '0029900005', '0029900006', '0029900007', '0029900088', '0029900099')
                        """)
                .update();
        jdbc.sql("DELETE FROM players WHERE nba_player_id IN (9000001001, 9000001002, 9000001003)")
                .update();
        jdbc.sql("DELETE FROM teams WHERE nba_team_id IN (9000000001, 9000000002, 9000000003)")
                .update();
    }

    static void insertAll(JdbcClient jdbc) {
        insertTeam(jdbc, HOME_TEAM, "THM", "Test Home");
        insertTeam(jdbc, AWAY_TEAM, "TAW", "Test Away");
        insertTeam(jdbc, ROSTER_TEAM, "TRS", "Test Roster");
        insertPlayer(jdbc, SUBJECT_PLAYER, "Subject", "Player", ROSTER_TEAM);
        insertPlayer(jdbc, OTHER_PLAYER, "Other", "Player", HOME_TEAM);
        insertPlayer(jdbc, INCONSISTENT_PLAYER, "Inconsistent", "Player", ROSTER_TEAM);

        insertGame(jdbc, HOME_GAME, "2024-25", "2024-10-22", HOME_TEAM, AWAY_TEAM);
        insertGame(jdbc, AWAY_GAME, "2024-25", "2024-10-24", AWAY_TEAM, HOME_TEAM);
        insertGame(jdbc, OTHER_OPPONENT_GAME, "2024-25", "2024-11-01", HOME_TEAM, ROSTER_TEAM);
        insertGame(jdbc, PRIOR_SEASON_GAME, "2023-24", "2023-12-01", HOME_TEAM, AWAY_TEAM);
        insertGame(jdbc, TIE_BREAK_LOW, "2024-25", "2024-12-01", HOME_TEAM, AWAY_TEAM);
        insertGame(jdbc, TIE_BREAK_HIGH, "2024-25", "2024-12-01", HOME_TEAM, AWAY_TEAM);
        insertGame(jdbc, OTHER_PLAYER_GAME, "2024-25", "2024-10-22", HOME_TEAM, AWAY_TEAM);
        insertGame(jdbc, INCONSISTENT_GAME, "2024-25", "2024-12-15", HOME_TEAM, AWAY_TEAM);

        insertStats(jdbc, HOME_GAME, SUBJECT_PLAYER, HOME_TEAM, "36.000", 30, 5, 8, 4);
        insertStats(jdbc, AWAY_GAME, SUBJECT_PLAYER, HOME_TEAM, "32.000", 20, 4, 6, 2);
        insertStats(jdbc, OTHER_OPPONENT_GAME, SUBJECT_PLAYER, HOME_TEAM, "34.000", 27, 5, 9, 5);
        insertStats(jdbc, PRIOR_SEASON_GAME, SUBJECT_PLAYER, HOME_TEAM, "30.000", 40, 10, 10, 6);
        insertStats(jdbc, TIE_BREAK_LOW, SUBJECT_PLAYER, HOME_TEAM, "28.000", 10, 1, 1, 0);
        insertStats(jdbc, TIE_BREAK_HIGH, SUBJECT_PLAYER, HOME_TEAM, "29.000", 11, 1, 1, 0);
        insertStats(jdbc, OTHER_PLAYER_GAME, OTHER_PLAYER, HOME_TEAM, "36.000", 99, 9, 9, 9);
        insertStats(jdbc, INCONSISTENT_GAME, INCONSISTENT_PLAYER, ROSTER_TEAM, "12.000", 8, 1, 1, 0);
    }

    static void insertTeam(JdbcClient jdbc, long nbaTeamId, String abbreviation, String nickname) {
        jdbc.sql("""
                        INSERT INTO teams (nba_team_id, abbreviation, full_name, nickname, city)
                        VALUES (:id, :abbr, :fullName, :nickname, :city)
                        """)
                .param("id", nbaTeamId)
                .param("abbr", abbreviation)
                .param("fullName", "City " + nickname)
                .param("nickname", nickname)
                .param("city", "City")
                .update();
    }

    static void insertPlayer(JdbcClient jdbc, long nbaPlayerId, String first, String last, long rosterTeam) {
        jdbc.sql("""
                        INSERT INTO players (
                            nba_player_id, first_name, last_name, full_name, is_active, nba_team_id)
                        VALUES (:id, :first, :last, :fullName, TRUE, :team)
                        """)
                .param("id", nbaPlayerId)
                .param("first", first)
                .param("last", last)
                .param("fullName", first + " " + last)
                .param("team", rosterTeam)
                .update();
    }

    static void insertGame(
            JdbcClient jdbc,
            String nbaGameId,
            String season,
            String gameDate,
            long homeTeam,
            long awayTeam) {
        jdbc.sql("""
                        INSERT INTO games (
                            nba_game_id, season, game_date, home_nba_team_id, away_nba_team_id,
                            home_score, away_score, status)
                        VALUES (:id, :season, CAST(:gameDate AS DATE), :home, :away, 100, 90, 'FINAL')
                        """)
                .param("id", nbaGameId)
                .param("season", season)
                .param("gameDate", gameDate)
                .param("home", homeTeam)
                .param("away", awayTeam)
                .update();
    }

    private static void insertStats(
            JdbcClient jdbc,
            String nbaGameId,
            long nbaPlayerId,
            long nbaTeamId,
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
                .param("player", nbaPlayerId)
                .param("team", nbaTeamId)
                .param("minutes", minutes)
                .param("points", points)
                .param("rebounds", rebounds)
                .param("assists", assists)
                .param("threes", threes)
                .update();
    }
}
