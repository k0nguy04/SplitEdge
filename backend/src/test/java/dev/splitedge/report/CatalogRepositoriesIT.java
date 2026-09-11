package dev.splitedge.report;

import static dev.splitedge.report.CatalogRepositoriesFixtures.ACTIVE_PLAYER;
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
}
