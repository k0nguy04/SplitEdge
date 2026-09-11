package dev.splitedge.report;

import java.util.List;
import java.util.Objects;

/**
 * Completed matchup calculation.
 *
 * {@code matchupGames} are in deterministic chronological order: game date ascending, then
 * zero-padded NBA game ID ascending.
 */
public record MatchupCalculation(
        SampleSummary matchup,
        SampleSummary baseline,
        SampleQuality sampleQuality,
        ExactFraction hitRateDifferencePoints,
        List<ClassifiedGame> matchupGames) {

    public MatchupCalculation {
        Objects.requireNonNull(matchup, "matchup");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(matchupGames, "matchupGames");
        matchupGames = List.copyOf(matchupGames);
    }

    /**
     * Classified matchup games ordered by game date ascending, then zero-padded NBA game ID
     * ascending. The returned list is unmodifiable.
     */
    @Override
    public List<ClassifiedGame> matchupGames() {
        return matchupGames;
    }
}
