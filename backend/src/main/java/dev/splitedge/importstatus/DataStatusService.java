package dev.splitedge.importstatus;

import java.util.Objects;

import org.springframework.stereotype.Service;

import dev.splitedge.report.GameSeasonRepository;
import dev.splitedge.report.ImportFreshnessRepository;

@Service
public class DataStatusService {

    private final ImportFreshnessRepository importFreshness;
    private final GameSeasonRepository gameSeasons;
    private final DataStatusRepository dataStatusRepository;

    public DataStatusService(
            ImportFreshnessRepository importFreshness,
            GameSeasonRepository gameSeasons,
            DataStatusRepository dataStatusRepository) {
        this.importFreshness = Objects.requireNonNull(importFreshness);
        this.gameSeasons = Objects.requireNonNull(gameSeasons);
        this.dataStatusRepository = Objects.requireNonNull(dataStatusRepository);
    }

    /**
     * READY requires a COMPLETED GAMES_STATS import run plus at least one stored game
     * and at least one stored player-game-stat row. Otherwise EMPTY, even when some
     * rows are already stored (a partial import).
     */
    public DataStatus currentStatus() {
        DataStatusCounts counts = dataStatusRepository.countAll();
        var latestRun = importFreshness.findLatestCompletedGamesStatsRun().orElse(null);
        boolean ready = latestRun != null && counts.games() > 0 && counts.playerGameStats() > 0;
        return new DataStatus(
                ready,
                ready ? DataStatusLevel.READY : DataStatusLevel.EMPTY,
                latestRun,
                gameSeasons.findStoredSeasons(),
                counts);
    }
}
