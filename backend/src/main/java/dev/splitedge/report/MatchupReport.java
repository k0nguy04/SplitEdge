package dev.splitedge.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrated report result. Values stay in their exact calculation form;
 * decimal rounding happens only in the API response mapper.
 */
public record MatchupReport(
        PlayerIdentity player,
        TeamIdentity opponent,
        MatchupReportCommand criteria,
        List<String> seasonsApplied,
        MatchupCalculation calculation,
        Instant gamesStatsCompletedAt) {

    public MatchupReport {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(calculation, "calculation");
        seasonsApplied = List.copyOf(seasonsApplied);
    }
}
