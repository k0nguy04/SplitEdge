package dev.splitedge.report;

import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER;
import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER_ADAMS;
import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER_NULL_TEAM;
import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER_TIE_HIGHER_ID;
import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER_TIE_LOWER_ID;
import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER_ZEBRA;
import static dev.splitedge.report.CatalogRepositoriesFixtures.FREE_AGENT_PLAYER;
import static dev.splitedge.report.CatalogRepositoriesFixtures.INACTIVE_PLAYER;
import static dev.splitedge.report.CatalogRepositoriesFixtures.TEAM_AAA;
import static dev.splitedge.report.CatalogRepositoriesFixtures.TEAM_ZZZ;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.splitedge.report.support.GuardedPostgres;
import dev.splitedge.report.support.IntegrationDatabaseSettings;

/**
 * Verifies {@link PlayerIdentityRepository#findProfileByNbaPlayerId} and
 * {@link TeamIdentityRepository#findAllOrderedByAbbreviation} against real stored V2
 * columns. Runs only against {@code splitedge_backend_test}.
 */
@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogRepositoriesIT {

    private JdbcClient jdbc;
    private PlayerIdentityRepository players;
    private TeamIdentityRepository teams;

    @BeforeAll
    void setUp() {
        DataSource dataSource =
                GuardedPostgres.connectAndMigrate(IntegrationDatabaseSettings.fromEnvironment());
        jdbc = JdbcClient.create(dataSource);
        players = new PlayerIdentityRepository(jdbc);
        teams = new TeamIdentityRepository(jdbc);
        CatalogRepositoriesFixtures.deleteAll(jdbc);
        CatalogRepositoriesFixtures.insertAll(jdbc);
    }

    @AfterAll
    void tearDown() {
        if (jdbc != null) {
            CatalogRepositoriesFixtures.deleteAll(jdbc);
        }
    }

    @Test
    void findsTheFullProfileForAnActivePlayerWithARosterTeam() {
        Optional<PlayerProfile> profile = players.findProfileByNbaPlayerId(ACTIVE_PLAYER);
        assertThat(profile).isPresent();
        assertThat(profile.get().nbaPlayerId()).isEqualTo(ACTIVE_PLAYER);
        assertThat(profile.get().firstName()).isEqualTo("Active");
        assertThat(profile.get().lastName()).isEqualTo("Player");
        assertThat(profile.get().fullName()).isEqualTo("Active Player");
        assertThat(profile.get().nbaTeamId()).isEqualTo(TEAM_ZZZ);
        assertThat(profile.get().active()).isTrue();
    }

    @Test
    void inactiveHistoricalPlayerRemainsQueryableWithActiveFalse() {
        Optional<PlayerProfile> profile = players.findProfileByNbaPlayerId(INACTIVE_PLAYER);
        assertThat(profile).isPresent();
        assertThat(profile.get().active()).isFalse();
        assertThat(profile.get().nbaTeamId()).isEqualTo(TEAM_AAA);
    }

    @Test
    void nullableCurrentTeamIsReadAsARealNullNotZero() {
        Optional<PlayerProfile> profile = players.findProfileByNbaPlayerId(FREE_AGENT_PLAYER);
        assertThat(profile).isPresent();
        assertThat(profile.get().nbaTeamId()).isNull();
    }

    @Test
    void unknownPlayerIdIsAbsent() {
        assertThat(players.findProfileByNbaPlayerId(9_999_999_999L)).isEmpty();
    }

    @Test
    void findActiveExcludesInactiveAndFreeAgentPlayers() {
        List<PlayerProfile> active = players.findActive(1000);
        assertThat(active)
                .extracting(PlayerProfile::nbaPlayerId)
                .doesNotContain(INACTIVE_PLAYER, FREE_AGENT_PLAYER);
    }

    /**
     * Distinguishes "excluded because inactive" from "excluded because the team is
     * null": {@link CatalogRepositoriesFixtures#FREE_AGENT_PLAYER} is inactive AND
     * rosterless, so on its own it can't prove which property actually drives the
     * exclusion. This test adds an ACTIVE rosterless player and an INACTIVE rostered
     * player so exclusion tracks {@code is_active} alone, never {@code nba_team_id}.
     */
    @Test
    void findActiveExclusionTracksTheActiveFlagNotTeamNullability() {
        List<PlayerProfile> active = players.findActive(1000);

        assertThat(active).extracting(PlayerProfile::nbaPlayerId).contains(ACTIVE_PLAYER_NULL_TEAM);
        assertThat(active)
                .extracting(PlayerProfile::nbaPlayerId)
                // INACTIVE_PLAYER has a real roster team (TEAM_AAA) yet is still excluded;
                // FREE_AGENT_PLAYER has no team and is also excluded - both exclusions are
                // explained entirely by is_active = FALSE, not by team nullability.
                .doesNotContain(INACTIVE_PLAYER, FREE_AGENT_PLAYER);
    }

    @Test
    void findActiveIncludesAnActiveFreeAgentWithNullTeam() {
        List<PlayerProfile> active = players.findActive(1000);

        PlayerProfile freeAgent = active.stream()
                .filter(player -> player.nbaPlayerId() == ACTIVE_PLAYER_NULL_TEAM)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected findActive() to include an active null-team player"));

        assertThat(freeAgent.active()).isTrue();
        assertThat(freeAgent.nbaTeamId()).isNull();
        assertThat(freeAgent.firstName()).isEqualTo("Undrafted");
        assertThat(freeAgent.lastName()).isEqualTo("Rookie");
    }

    @Test
    void findActiveOrdersByLastNameThenFirstNameThenNbaPlayerId() {
        List<PlayerProfile> active = players.findActive(1000);

        int adamsIndex = indexOfPlayer(active, ACTIVE_PLAYER_ADAMS);
        int tieLowerIndex = indexOfPlayer(active, ACTIVE_PLAYER_TIE_LOWER_ID);
        int tieHigherIndex = indexOfPlayer(active, ACTIVE_PLAYER_TIE_HIGHER_ID);
        int activePlayerIndex = indexOfPlayer(active, ACTIVE_PLAYER);
        int zebraIndex = indexOfPlayer(active, ACTIVE_PLAYER_ZEBRA);

        assertThat(adamsIndex).isGreaterThanOrEqualTo(0);
        assertThat(tieLowerIndex).isGreaterThanOrEqualTo(0);
        assertThat(tieHigherIndex).isGreaterThanOrEqualTo(0);
        assertThat(activePlayerIndex).isGreaterThanOrEqualTo(0);
        assertThat(zebraIndex).isGreaterThanOrEqualTo(0);

        // last_name order: Adams < Curry (tie, then nba_player_id ASC) < Player < Zebra
        assertThat(adamsIndex).isLessThan(tieLowerIndex);
        assertThat(tieLowerIndex).isLessThan(tieHigherIndex);
        assertThat(tieHigherIndex).isLessThan(activePlayerIndex);
        assertThat(activePlayerIndex).isLessThan(zebraIndex);
    }

    @Test
    void findActiveRespectsTheLimitParameter() {
        assertThat(players.findActive(1)).hasSize(1);
    }

    @Test
    void teamsAreOrderedByAbbreviationThenNbaTeamIdWithRealFields() {
        List<TeamProfile> all = teams.findAllOrderedByAbbreviation();
        int aaaIndex = indexOfTeam(all, TEAM_AAA);
        int zzzIndex = indexOfTeam(all, TEAM_ZZZ);
        assertThat(aaaIndex).isGreaterThanOrEqualTo(0);
        assertThat(zzzIndex).isGreaterThanOrEqualTo(0);
        assertThat(aaaIndex).isLessThan(zzzIndex);

        TeamProfile aaa = all.get(aaaIndex);
        assertThat(aaa.abbreviation()).isEqualTo("AAA");
        assertThat(aaa.city()).isEqualTo("Alpha City");
        assertThat(aaa.nickname()).isEqualTo("Aces");
        assertThat(aaa.fullName()).isEqualTo("Alpha City Aces");

        TeamProfile zzz = all.get(zzzIndex);
        assertThat(zzz.abbreviation()).isEqualTo("ZZZ");
        assertThat(zzz.city()).isEqualTo("Zulu City");
        assertThat(zzz.nickname()).isEqualTo("Zetas");
        assertThat(zzz.fullName()).isEqualTo("Zulu City Zetas");
    }

    private static int indexOfTeam(List<TeamProfile> teams, long nbaTeamId) {
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).nbaTeamId() == nbaTeamId) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfPlayer(List<PlayerProfile> players, long nbaPlayerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).nbaPlayerId() == nbaPlayerId) {
                return i;
            }
        }
        return -1;
    }
}
