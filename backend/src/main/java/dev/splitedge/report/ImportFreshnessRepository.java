package dev.splitedge.report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ImportFreshnessRepository {

    public static final String GAMES_STATS = "GAMES_STATS";

    private static final String LATEST_COMPLETED = """
            SELECT completed_at
            FROM import_runs
            WHERE import_type = :importType
              AND status = 'COMPLETED'
              AND completed_at IS NOT NULL
            ORDER BY completed_at DESC
            LIMIT 1
            """;

    private static final String LATEST_COMPLETED_RUN = """
            SELECT id, completed_at, records_processed, records_failed
            FROM import_runs
            WHERE import_type = :importType
              AND status = 'COMPLETED'
              AND completed_at IS NOT NULL
            ORDER BY completed_at DESC, id DESC
            LIMIT 1
            """;

    private final JdbcClient jdbc;

    public ImportFreshnessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Instant> findLatestCompletedGamesStats() {
        return jdbc.sql(LATEST_COMPLETED)
                .param("importType", GAMES_STATS)
                .query((rs, rowNum) -> {
                    OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
                    return completedAt == null ? null : completedAt.toInstant();
                })
                .optional();
    }

    /**
     * Latest COMPLETED GAMES_STATS import run, excluding RUNNING/FAILED runs and every
     * other import type. Ties on {@code completed_at} are broken by the higher run ID.
     */
    public Optional<ImportRunSummary> findLatestCompletedGamesStatsRun() {
        return jdbc.sql(LATEST_COMPLETED_RUN)
                .param("importType", GAMES_STATS)
                .query((rs, rowNum) -> {
                    OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
                    return new ImportRunSummary(
                            rs.getLong("id"),
                            completedAt == null ? null : completedAt.toInstant(),
                            rs.getInt("records_processed"),
                            rs.getInt("records_failed"));
                })
                .optional();
    }
}
