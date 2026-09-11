package dev.splitedge.importstatus;

import java.time.Instant;
import java.util.List;

public record DataStatusResponse(
        boolean dataImported,
        String status,
        LatestGamesStatsImport latestGamesStatsImport,
        List<String> seasons,
        Counts counts) {

    public record LatestGamesStatsImport(long runId, Instant completedAt, int recordsProcessed, int recordsFailed) {}

    public record Counts(long games, long playerGameStats, long players, long activePlayers, long teams) {}
}
