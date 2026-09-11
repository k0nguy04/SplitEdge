package dev.splitedge.report.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import dev.splitedge.report.ClassifiedGame;
import dev.splitedge.report.ExactFraction;
import dev.splitedge.report.ImportFreshnessRepository;
import dev.splitedge.report.MatchupReport;
import dev.splitedge.report.MatchupReportCommand;
import dev.splitedge.report.SampleSummary;

/**
 * Converts exact calculation values into response decimals. This is the only place
 * presentation rounding is applied.
 */
public final class MatchupReportResponseMapper {

    static final int RATE_SCALE = 3;
    static final int AVERAGE_SCALE = 1;
    static final String NO_QUALIFYING_GAMES = "NO_QUALIFYING_GAMES";
    static final String ONLY_PUSHES = "ONLY_PUSHES";
    static final String NO_SUCCESSFUL_GAMES_STATS_IMPORT = "NO_SUCCESSFUL_GAMES_STATS_IMPORT";
    private static final String TEAM_DEFENSE_NOT_IMPORTED = "TEAM_DEFENSE_NOT_IMPORTED";

    private MatchupReportResponseMapper() {}

    public static MatchupReportResponse toResponse(MatchupReport report) {
        List<ClassifiedGame> games = report.calculation().matchupGames();
        return new MatchupReportResponse(
                criteria(report),
                sample(report.calculation().matchup()),
                sample(report.calculation().baseline()),
                new MatchupReportResponse.Comparison(
                        decimal(report.calculation().hitRateDifferencePoints(), AVERAGE_SCALE)),
                report.calculation().sampleQuality(),
                new MatchupReportResponse.DataFreshness(
                        ImportFreshnessRepository.GAMES_STATS, report.gamesStatsCompletedAt()),
                new MatchupReportResponse.OpponentContext(false, TEAM_DEFENSE_NOT_IMPORTED),
                games(games, report.criteria()),
                chart(games),
                warnings(report));
    }

    private static MatchupReportResponse.Criteria criteria(MatchupReport report) {
        MatchupReportCommand criteria = report.criteria();
        return new MatchupReportResponse.Criteria(
                new MatchupReportResponse.Player(report.player().nbaPlayerId(), report.player().fullName()),
                new MatchupReportResponse.Opponent(
                        report.opponent().nbaTeamId(),
                        report.opponent().abbreviation(),
                        report.opponent().fullName()),
                criteria.prop().name(),
                criteria.line().value(),
                criteria.direction().name(),
                report.seasonsApplied(),
                criteria.location().name(),
                criteria.recency().name(),
                criteria.minMinutes().value());
    }

    private static MatchupReportResponse.Sample sample(SampleSummary summary) {
        return new MatchupReportResponse.Sample(
                summary.qualifyingGames(),
                summary.hits(),
                summary.misses(),
                summary.pushes(),
                decimal(summary.hitRate(), RATE_SCALE),
                decimal(summary.average(), AVERAGE_SCALE),
                summary.median() == null ? null : summary.median().setScale(AVERAGE_SCALE, RoundingMode.HALF_UP));
    }

    private static List<MatchupReportResponse.Game> games(
            List<ClassifiedGame> games, MatchupReportCommand criteria) {
        List<MatchupReportResponse.Game> mapped = new ArrayList<>(games.size());
        for (ClassifiedGame classified : games) {
            mapped.add(new MatchupReportResponse.Game(
                    classified.game().nbaGameId(),
                    classified.game().gameDate(),
                    classified.game().season(),
                    classified.game().location().name(),
                    classified.game().nbaTeamId(),
                    classified.game().opponentNbaTeamId(),
                    classified.game().minutes(),
                    classified.propValue(),
                    criteria.line().value(),
                    classified.result()));
        }
        return List.copyOf(mapped);
    }

    private static List<MatchupReportResponse.ChartPoint> chart(List<ClassifiedGame> games) {
        List<MatchupReportResponse.ChartPoint> mapped = new ArrayList<>(games.size());
        for (ClassifiedGame classified : games) {
            mapped.add(new MatchupReportResponse.ChartPoint(
                    classified.game().nbaGameId(),
                    classified.game().gameDate(),
                    classified.propValue(),
                    classified.result()));
        }
        return List.copyOf(mapped);
    }

    private static List<String> warnings(MatchupReport report) {
        SampleSummary matchup = report.calculation().matchup();
        List<String> warnings = new ArrayList<>(2);
        if (matchup.qualifyingGames() == 0) {
            warnings.add(NO_QUALIFYING_GAMES);
        } else if (matchup.hits() + matchup.misses() == 0) {
            warnings.add(ONLY_PUSHES);
        }
        if (report.gamesStatsCompletedAt() == null) {
            warnings.add(NO_SUCCESSFUL_GAMES_STATS_IMPORT);
        }
        return List.copyOf(warnings);
    }

    static BigDecimal decimal(ExactFraction fraction, int scale) {
        if (fraction == null) {
            return null;
        }
        return BigDecimal.valueOf(fraction.numerator())
                .divide(BigDecimal.valueOf(fraction.denominator()), scale, RoundingMode.HALF_UP);
    }
}
