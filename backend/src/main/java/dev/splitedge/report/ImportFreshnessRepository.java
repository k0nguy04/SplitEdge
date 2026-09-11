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
}
