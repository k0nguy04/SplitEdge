package dev.splitedge.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.splitedge.report.ClassifiedGame;
import dev.splitedge.report.Direction;
import dev.splitedge.report.ExactFraction;
import dev.splitedge.report.GameLocation;
import dev.splitedge.report.GameResult;
import dev.splitedge.report.Line;
import dev.splitedge.report.LocationFilter;
import dev.splitedge.report.MatchupCalculation;
import dev.splitedge.report.MatchupCalculator;
import dev.splitedge.report.MatchupReport;
import dev.splitedge.report.MatchupReportCommand;
import dev.splitedge.report.MinMinutes;
import dev.splitedge.report.PlayerGameLine;
import dev.splitedge.report.PlayerIdentity;
import dev.splitedge.report.PropType;
import dev.splitedge.report.RecencyFilter;
import dev.splitedge.report.SampleQuality;
import dev.splitedge.report.SampleSummary;
import dev.splitedge.report.TeamIdentity;

final class MatchupReportFixtures {

    static final long PLAYER_ID = 201_939L;
    static final long TEAM_ID = 1_610_612_744L;
    static final long OPPONENT_ID = 1_610_612_738L;
    static final long OTHER_TEAM_ID = 1_610_612_752L;
    static final Instant COMPLETED_AT = Instant.parse("2025-03-02T08:15:30Z");

    private MatchupReportFixtures() {}

    static MatchupReportCommand command() {
        return new MatchupReportCommand(
                PLAYER_ID,
                OPPONENT_ID,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                "2024-25",
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
    }

    /**
     * Built by running the real {@link MatchupCalculator} over four stored rows so the
     * matchup and baseline summaries are mathematically reachable, not hand-picked.
     *
     * <p>Three rows target {@code OPPONENT_ID}: one HIT (30 points) and two MISSes (20
     * points each), giving the matchup an exact 1/3 hit rate. A fourth row targets a
     * different team ({@code OTHER_TEAM_ID}) with 28 points, a HIT. Because location and
     * recency are both ALL, the baseline (no opponent filter) contains all four rows: the
     * same three matchup games plus this extra non-opponent game, giving the baseline an
     * exact 4-game, 2-hit / 2-miss sample with a 1/2 hit rate. The exact percentage-point
     * difference is (1/3 - 1/2) x 100 = -50/3, which the response mapper presents as -16.7.
     */
    static MatchupReport oneThirdReport() {
        List<PlayerGameLine> rows = List.of(
                gameLine("0022400001", LocalDate.of(2024, 10, 22), GameLocation.HOME, OPPONENT_ID, 30),
                gameLine("0022400002", LocalDate.of(2024, 11, 1), GameLocation.AWAY, OPPONENT_ID, 20),
                gameLine("0022400003", LocalDate.of(2024, 12, 1), GameLocation.HOME, OPPONENT_ID, 20),
                gameLine("0022400004", LocalDate.of(2024, 11, 15), GameLocation.HOME, OTHER_TEAM_ID, 28));
        MatchupCalculation calculation = new MatchupCalculator().calculate(rows, command().toQuery());
        return report(calculation, COMPLETED_AT);
    }

    static MatchupReport emptyReport(Instant completedAt) {
        SampleSummary empty = new SampleSummary(0, 0, 0, 0, null, null, null);
        return report(new MatchupCalculation(empty, empty, null, null, List.of()), completedAt);
    }

    static MatchupReport onlyPushReport() {
        List<ClassifiedGame> games = List.of(
                classified("0022400001", LocalDate.of(2024, 10, 22), GameLocation.HOME, 24, GameResult.PUSH),
                classified("0022400002", LocalDate.of(2024, 11, 1), GameLocation.HOME, 24, GameResult.PUSH));
        SampleSummary onlyPushes = new SampleSummary(
                2, 0, 0, 2, null, ExactFraction.of(48, 2), new BigDecimal("24"));
        SampleSummary baseline = new SampleSummary(
                3, 1, 0, 2, ExactFraction.of(1, 1), ExactFraction.of(78, 3), new BigDecimal("24"));
        return report(
                new MatchupCalculation(onlyPushes, baseline, SampleQuality.LOW, null, games), COMPLETED_AT);
    }

    static MatchupReport report(MatchupCalculation calculation, Instant completedAt) {
        return new MatchupReport(
                new PlayerIdentity(PLAYER_ID, "Test Player"),
                new TeamIdentity(OPPONENT_ID, "BOS", "Boston Celtics"),
                command(),
                List.of("2024-25"),
                calculation,
                completedAt);
    }

    static ClassifiedGame classified(
            String nbaGameId, LocalDate date, GameLocation location, int points, GameResult result) {
        return new ClassifiedGame(
                gameLine(nbaGameId, date, location, OPPONENT_ID, points),
                BigDecimal.valueOf(points),
                result);
    }

    static PlayerGameLine gameLine(
            String nbaGameId, LocalDate date, GameLocation location, long opponentNbaTeamId, int points) {
        return new PlayerGameLine(
                nbaGameId,
                date,
                "2024-25",
                location,
                PLAYER_ID,
                TEAM_ID,
                opponentNbaTeamId,
                new BigDecimal("36.000"),
                points,
                5,
                8,
                4);
    }
}
