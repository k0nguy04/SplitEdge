package dev.splitedge.importstatus;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.splitedge.report.GameSeasonRepository;
import dev.splitedge.report.ImportFreshnessRepository;
import dev.splitedge.report.ImportRunSummary;
import dev.splitedge.report.support.GuardedPostgres;
import dev.splitedge.report.support.IntegrationDatabaseSettings;

/**
 * Verifies {@link DataStatusService} end-to-end against real PostgreSQL: bounded
 * aggregate counts, the latest-COMPLETED-GAMES_STATS selection with its ID tiebreak,
 * and the READY/EMPTY decision. Runs only against {@code splitedge_backend_test}.
 *
 * <p>{@code games}/{@code players}/{@code teams}/{@code player_game_stats} fixture rows
 * are scoped by dedicated IDs and cleaned up in both directions. {@code import_runs} has
 * no natural per-row scope, so it is fully owned for this class's lifetime: every test
 * that mutates it restores the class's baseline COMPLETED run afterward, so tests remain
 * repeatable regardless of execution order.
 */
@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataStatusServiceIT {

    private JdbcClient jdbc;
    private DataStatusService service;
    private ImportFreshnessRepository importFreshness;

    @BeforeAll
    void setUp() {
        DataSource dataSource =
                GuardedPostgres.connectAndMigrate(IntegrationDatabaseSettings.fromEnvironment());
        jdbc = JdbcClient.create(dataSource);
        importFreshness = new ImportFreshnessRepository(jdbc);
        service = new DataStatusService(
                importFreshness, new GameSeasonRepository(jdbc), new DataStatusRepository(jdbc));

        DataStatusServiceItFixtures.deleteScopedRows(jdbc);
        DataStatusServiceItFixtures.insertScopedRows(jdbc);
        DataStatusServiceItFixtures.wipeImportRuns(jdbc);
        DataStatusServiceItFixtures.insertBaselineCompletedRun(jdbc);
    }

    @AfterAll
    void tearDown() {
        if (jdbc != null) {
            DataStatusServiceItFixtures.deleteScopedRows(jdbc);
            DataStatusServiceItFixtures.wipeImportRuns(jdbc);
        }
    }

    @Test
    void readyStatusReflectsRealCountsSeasonsAndTheBaselineCompletedRun() {
        DataStatus status = service.currentStatus();

        assertThat(status.dataImported()).isTrue();
        assertThat(status.status()).isEqualTo(DataStatusLevel.READY);

        ImportRunSummary latest = status.latestGamesStatsImport();
        assertThat(latest).isNotNull();
        assertThat(latest.recordsProcessed()).isEqualTo(DataStatusServiceItFixtures.BASELINE_RUN_RECORDS_PROCESSED);
        assertThat(latest.recordsFailed()).isEqualTo(DataStatusServiceItFixtures.BASELINE_RUN_RECORDS_FAILED);

        assertThat(status.seasons()).contains("2024-25");

        // Bounded aggregate counts must exactly match direct raw SQL counts at this
        // instant: this is the real cross-check that "real counts" are not hard-coded.
        assertThat(status.counts().games()).isEqualTo(rawCount("games"));
        assertThat(status.counts().playerGameStats()).isEqualTo(rawCount("player_game_stats"));
        assertThat(status.counts().players()).isEqualTo(rawCount("players"));
        assertThat(status.counts().teams()).isEqualTo(rawCount("teams"));
        assertThat(status.counts().activePlayers())
                .isEqualTo(rawCount("players", "is_active = TRUE"));

        // And they must reflect at least the fixture rows this class seeded.
        assertThat(status.counts().games()).isGreaterThanOrEqualTo(2);
        assertThat(status.counts().playerGameStats()).isGreaterThanOrEqualTo(3);
        assertThat(status.counts().teams()).isGreaterThanOrEqualTo(2);
        assertThat(status.counts().players()).isGreaterThanOrEqualTo(3);
        assertThat(status.counts().activePlayers()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void excludesRunningFailedAndOtherImportTypesAndTiebreaksTheLatestCompletedRunByIdDescending() {
        DataStatusServiceItFixtures.wipeImportRuns(jdbc);
        try {
            DataStatusServiceItFixtures.insertImportRun(jdbc, "GAMES_STATS", "RUNNING", null, 0, 0);
            DataStatusServiceItFixtures.insertImportRun(
                    jdbc, "GAMES_STATS", "FAILED", "2025-07-01T00:00:00Z", 0, 5);
            DataStatusServiceItFixtures.insertImportRun(
                    jdbc, "TEAMS_PLAYERS", "COMPLETED", "2025-08-01T00:00:00Z", 30, 0);
            // Two COMPLETED GAMES_STATS runs sharing the same completed_at: the later
            // insert (higher ID) must win the tiebreak.
            DataStatusServiceItFixtures.insertImportRun(
                    jdbc, "GAMES_STATS", "COMPLETED", "2025-08-15T00:00:00Z", 111, 0);
            DataStatusServiceItFixtures.insertImportRun(
                    jdbc, "GAMES_STATS", "COMPLETED", "2025-08-15T00:00:00Z", 222, 0);

            var latest = importFreshness.findLatestCompletedGamesStatsRun();
            assertThat(latest).isPresent();
            assertThat(latest.get().recordsProcessed()).isEqualTo(222);
        } finally {
            DataStatusServiceItFixtures.wipeImportRuns(jdbc);
            DataStatusServiceItFixtures.insertBaselineCompletedRun(jdbc);
        }
    }

    @Test
    void emptyWhenNoCompletedGamesStatsImportExistsDespiteStoredGamesAndStats() {
        DataStatusServiceItFixtures.wipeImportRuns(jdbc);
        try {
            DataStatus status = service.currentStatus();
            assertThat(status.dataImported()).isFalse();
            assertThat(status.status()).isEqualTo(DataStatusLevel.EMPTY);
            assertThat(status.latestGamesStatsImport()).isNull();
            // Games/stats fixtures are untouched, proving EMPTY here is caused only by
            // the missing completed import, not by missing rows.
            assertThat(status.counts().games()).isGreaterThanOrEqualTo(2);
            assertThat(status.counts().playerGameStats()).isGreaterThanOrEqualTo(3);
        } finally {
            DataStatusServiceItFixtures.wipeImportRuns(jdbc);
            DataStatusServiceItFixtures.insertBaselineCompletedRun(jdbc);
        }
    }

    private long rawCount(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private long rawCount(String table, String whereClause) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + whereClause)
                .query(Long.class)
                .single();
    }
}
