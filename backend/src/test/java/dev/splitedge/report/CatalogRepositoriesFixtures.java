package dev.splitedge.report;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Fixture rows scoped to this class's own disjoint IDs, disjoint from every other IT fixture. */
final class CatalogRepositoriesFixtures {

    static final long TEAM_ZZZ = 9_000_000_031L;
    static final long TEAM_AAA = 9_000_000_032L;

    static final long ACTIVE_PLAYER = 9_000_003_001L;
    static final long INACTIVE_PLAYER = 9_000_003_002L;
    static final long FREE_AGENT_PLAYER = 9_000_003_003L;
    static final long ACTIVE_PLAYER_ADAMS = 9_000_003_004L;
    static final long ACTIVE_PLAYER_ZEBRA = 9_000_003_005L;
    static final long ACTIVE_PLAYER_TIE_LOWER_ID = 9_000_003_006L;
    static final long ACTIVE_PLAYER_TIE_HIGHER_ID = 9_000_003_007L;

    /**
     * Active AND rosterless: {@code is_active = TRUE} with a NULL {@code nba_team_id}.
     * Distinct from {@link #FREE_AGENT_PLAYER}, which is rosterless but INACTIVE - this
     * is the fixture that actually proves {@code findActive()} includes active players
     * regardless of team nullability, rather than accidentally excluding null-team rows.
     */
    static final long ACTIVE_PLAYER_NULL_TEAM = 9_000_003_008L;

    private CatalogRepositoriesFixtures() {}

    static void deleteAll(JdbcClient jdbc) {
        jdbc.sql("""
                        DELETE FROM players WHERE nba_player_id IN (
                            9000003001, 9000003002, 9000003003, 9000003004, 9000003005,
                            9000003006, 9000003007, 9000003008)
                        """)
                .update();
        jdbc.sql("DELETE FROM teams WHERE nba_team_id IN (9000000031, 9000000032)").update();
    }

    static void insertAll(JdbcClient jdbc) {
        insertTeam(jdbc, TEAM_ZZZ, "ZZZ", "Zulu City", "Zetas", "Zulu City Zetas");
        insertTeam(jdbc, TEAM_AAA, "AAA", "Alpha City", "Aces", "Alpha City Aces");

        insertPlayer(jdbc, ACTIVE_PLAYER, "Active", "Player", TEAM_ZZZ, true);
        insertPlayer(jdbc, INACTIVE_PLAYER, "Retired", "Player", TEAM_AAA, false);
        insertPlayerWithNullTeam(jdbc, FREE_AGENT_PLAYER, "Free", "Agent", false);
        insertPlayerWithNullTeam(jdbc, ACTIVE_PLAYER_NULL_TEAM, "Undrafted", "Rookie", true);

        // Active fixtures for findActive() ordering/limit coverage: last name ASC,
        // first name ASC, then NBA player ID ASC as the final tiebreak.
        insertPlayer(jdbc, ACTIVE_PLAYER_ADAMS, "Bravo", "Adams", TEAM_ZZZ, true);
        insertPlayer(jdbc, ACTIVE_PLAYER_ZEBRA, "Alpha", "Zebra", TEAM_ZZZ, true);
        insertPlayer(jdbc, ACTIVE_PLAYER_TIE_LOWER_ID, "Adam", "Curry", TEAM_ZZZ, true);
        insertPlayer(jdbc, ACTIVE_PLAYER_TIE_HIGHER_ID, "Adam", "Curry", TEAM_ZZZ, true);
    }

    private static void insertTeam(
            JdbcClient jdbc, long nbaTeamId, String abbreviation, String city, String nickname, String fullName) {
        jdbc.sql("""
                        INSERT INTO teams (nba_team_id, abbreviation, full_name, nickname, city)
                        VALUES (:id, :abbr, :fullName, :nickname, :city)
                        """)
                .param("id", nbaTeamId)
                .param("abbr", abbreviation)
                .param("fullName", fullName)
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

    private static void insertPlayerWithNullTeam(
            JdbcClient jdbc, long nbaPlayerId, String first, String last, boolean active) {
        jdbc.sql("""
                        INSERT INTO players (
                            nba_player_id, first_name, last_name, full_name, is_active, nba_team_id)
                        VALUES (:id, :first, :last, :fullName, :active, NULL)
                        """)
                .param("id", nbaPlayerId)
                .param("first", first)
                .param("last", last)
                .param("fullName", first + " " + last)
                .param("active", active)
                .update();
    }
}
