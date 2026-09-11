package dev.splitedge.importstatus;

import java.util.List;
import java.util.Objects;

import dev.splitedge.report.ImportRunSummary;

/**
 * Real PostgreSQL-backed data status. {@code latestGamesStatsImport} is {@code null}
 * only when no COMPLETED GAMES_STATS import run exists yet; it is still populated when
 * {@code status} is {@link DataStatusLevel#EMPTY} because a completed run exists but no
 * games or player-game-stats rows are stored yet.
 */
public record DataStatus(
        boolean dataImported,
        DataStatusLevel status,
        ImportRunSummary latestGamesStatsImport,
        List<String> seasons,
        DataStatusCounts counts) {

    public DataStatus {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(counts, "counts");
        seasons = List.copyOf(seasons);
    }
}
