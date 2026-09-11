package dev.splitedge.importstatus;

public final class DataStatusResponseMapper {

    private DataStatusResponseMapper() {}

    public static DataStatusResponse toResponse(DataStatus status) {
        return new DataStatusResponse(
                status.dataImported(),
                status.status().name(),
                toLatestImport(status),
                status.seasons(),
                toCounts(status.counts()));
    }

    private static DataStatusResponse.LatestGamesStatsImport toLatestImport(DataStatus status) {
        var latest = status.latestGamesStatsImport();
        if (latest == null) {
            return null;
        }
        return new DataStatusResponse.LatestGamesStatsImport(
                latest.runId(), latest.completedAt(), latest.recordsProcessed(), latest.recordsFailed());
    }

    private static DataStatusResponse.Counts toCounts(DataStatusCounts counts) {
        return new DataStatusResponse.Counts(
                counts.games(), counts.playerGameStats(), counts.players(), counts.activePlayers(), counts.teams());
    }
}
