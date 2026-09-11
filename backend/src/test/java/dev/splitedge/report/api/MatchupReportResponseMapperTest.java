package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.splitedge.report.ExactFraction;
import dev.splitedge.report.GameResult;
import dev.splitedge.report.MatchupReport;

class MatchupReportResponseMapperTest {

    @Test
    void convertsExactFractionsWithHalfUpOnlyAtTheResponseBoundary() {
        // Repeating fractions: ordinary HALF_UP rounding, not a tie.
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(1, 3), 3))
                .isEqualTo(new BigDecimal("0.333"));
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(2, 3), 3))
                .isEqualTo(new BigDecimal("0.667"));
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(70, 3), 1))
                .isEqualTo(new BigDecimal("23.3"));
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(-50, 3), 1))
                .isEqualTo(new BigDecimal("-16.7"));
        // Already exact at the target scale: verifies sign handling, not tie-breaking.
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(-25, 2), 1))
                .isEqualTo(new BigDecimal("-12.5"));
        // Genuine negative HALF_UP tie: -49/4 = -12.25 lands exactly on the rounding
        // boundary at scale 1 and must round away from zero, to -12.3.
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(-49, 4), 1))
                .isEqualTo(new BigDecimal("-12.3"));
        // Genuine positive HALF_UP tie, for symmetry: 49/4 = 12.25 -> 12.3.
        assertThat(MatchupReportResponseMapper.decimal(ExactFraction.of(49, 4), 1))
                .isEqualTo(new BigDecimal("12.3"));
        assertThat(MatchupReportResponseMapper.decimal(null, 3)).isNull();
    }

    @Test
    void mapsSamplesComparisonAndCriteriaFromExactValues() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(MatchupReportFixtures.oneThirdReport());
        assertThat(response.matchup().qualifyingGames()).isEqualTo(3);
        assertThat(response.matchup().hits()).isEqualTo(1);
        assertThat(response.matchup().misses()).isEqualTo(2);
        assertThat(response.matchup().pushes()).isZero();
        assertThat(response.matchup().hitRate()).isEqualTo(new BigDecimal("0.333"));
        assertThat(response.matchup().average()).isEqualTo(new BigDecimal("23.3"));
        assertThat(response.matchup().median()).isEqualTo(new BigDecimal("20.0"));
        assertThat(response.baseline().qualifyingGames()).isEqualTo(4);
        assertThat(response.baseline().hits()).isEqualTo(2);
        assertThat(response.baseline().misses()).isEqualTo(2);
        assertThat(response.baseline().pushes()).isZero();
        assertThat(response.baseline().hitRate()).isEqualTo(new BigDecimal("0.500"));
        assertThat(response.baseline().average()).isEqualTo(new BigDecimal("24.5"));
        assertThat(response.baseline().median()).isEqualTo(new BigDecimal("24.0"));
        assertThat(response.comparison().hitRateDifferencePoints()).isEqualTo(new BigDecimal("-16.7"));
        assertThat(response.criteria().player().nbaPlayerId()).isEqualTo(MatchupReportFixtures.PLAYER_ID);
        assertThat(response.criteria().opponent().abbreviation()).isEqualTo("BOS");
        assertThat(response.criteria().prop()).isEqualTo("POINTS");
        assertThat(response.criteria().direction()).isEqualTo("OVER");
        assertThat(response.criteria().location()).isEqualTo("ALL");
        assertThat(response.criteria().recency()).isEqualTo("ALL");
        assertThat(response.criteria().seasonsApplied()).containsExactly("2024-25");
        assertThat(response.criteria().line()).isEqualByComparingTo(new BigDecimal("24.5"));
        assertThat(response.criteria().minMinutes()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void supportingGamesAndChartStayChronologicalAndCarryNoScores() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(MatchupReportFixtures.oneThirdReport());
        assertThat(response.games())
                .extracting(MatchupReportResponse.Game::nbaGameId)
                .containsExactly("0022400001", "0022400002", "0022400003");
        assertThat(response.chart())
                .extracting(MatchupReportResponse.ChartPoint::nbaGameId)
                .containsExactly("0022400001", "0022400002", "0022400003");
        MatchupReportResponse.Game first = response.games().getFirst();
        assertThat(first.gameDate()).isEqualTo(LocalDate.of(2024, 10, 22));
        assertThat(first.season()).isEqualTo("2024-25");
        assertThat(first.location()).isEqualTo("HOME");
        assertThat(first.nbaTeamId()).isEqualTo(MatchupReportFixtures.TEAM_ID);
        assertThat(first.opponentNbaTeamId()).isEqualTo(MatchupReportFixtures.OPPONENT_ID);
        assertThat(first.minutes()).isEqualByComparingTo(new BigDecimal("36.000"));
        assertThat(first.propValue()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(first.line()).isEqualByComparingTo(new BigDecimal("24.5"));
        assertThat(first.result()).isEqualTo(GameResult.HIT);
        assertThat(MatchupReportResponse.Game.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("homeScore", "awayScore", "score");
        assertThat(response.chart().getFirst().propValue()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(response.chart().getFirst().result()).isEqualTo(GameResult.HIT);
    }

    @Test
    void gamesAndChartCardinalityAndContentAlwaysMatchTheMatchupSummary() {
        for (MatchupReport report : List.of(
                MatchupReportFixtures.oneThirdReport(),
                MatchupReportFixtures.onlyPushReport(),
                MatchupReportFixtures.emptyReport(MatchupReportFixtures.COMPLETED_AT))) {
            MatchupReportResponse response = MatchupReportResponseMapper.toResponse(report);

            assertThat(response.games()).hasSize(response.matchup().qualifyingGames());
            assertThat(response.chart()).hasSize(response.games().size());

            assertThat(response.chart())
                    .extracting(MatchupReportResponse.ChartPoint::nbaGameId)
                    .containsExactlyElementsOf(
                            response.games().stream().map(MatchupReportResponse.Game::nbaGameId).toList());
            assertThat(response.chart())
                    .extracting(MatchupReportResponse.ChartPoint::gameDate)
                    .containsExactlyElementsOf(
                            response.games().stream().map(MatchupReportResponse.Game::gameDate).toList());
            assertThat(response.chart())
                    .extracting(MatchupReportResponse.ChartPoint::propValue)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactlyElementsOf(
                            response.games().stream().map(MatchupReportResponse.Game::propValue).toList());
            assertThat(response.chart())
                    .extracting(MatchupReportResponse.ChartPoint::result)
                    .containsExactlyElementsOf(
                            response.games().stream().map(MatchupReportResponse.Game::result).toList());
        }
    }

    @Test
    void opponentContextIsUnavailableAndFreshnessIsReported() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(MatchupReportFixtures.oneThirdReport());
        assertThat(response.opponentContext().available()).isFalse();
        assertThat(response.opponentContext().reason()).isEqualTo("TEAM_DEFENSE_NOT_IMPORTED");
        assertThat(response.dataFreshness().importType()).isEqualTo("GAMES_STATS");
        assertThat(response.dataFreshness().completedAt()).isEqualTo(MatchupReportFixtures.COMPLETED_AT);
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void emptySampleEmitsNullRatesAndNoQualifyingGamesWarning() {
        MatchupReportResponse response = MatchupReportResponseMapper.toResponse(
                MatchupReportFixtures.emptyReport(MatchupReportFixtures.COMPLETED_AT));
        assertThat(response.matchup().qualifyingGames()).isZero();
        assertThat(response.matchup().hitRate()).isNull();
        assertThat(response.matchup().average()).isNull();
        assertThat(response.matchup().median()).isNull();
        assertThat(response.comparison().hitRateDifferencePoints()).isNull();
        assertThat(response.sampleQuality()).isNull();
        assertThat(response.games()).isEmpty();
        assertThat(response.chart()).isEmpty();
        assertThat(response.warnings()).containsExactly("NO_QUALIFYING_GAMES");
    }

    @Test
    void onlyPushSampleEmitsOnlyPushesWarningWithNullRateAndDifference() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(MatchupReportFixtures.onlyPushReport());
        assertThat(response.matchup().qualifyingGames()).isEqualTo(2);
        assertThat(response.matchup().pushes()).isEqualTo(2);
        assertThat(response.matchup().hitRate()).isNull();
        assertThat(response.matchup().average()).isEqualTo(new BigDecimal("24.0"));
        assertThat(response.comparison().hitRateDifferencePoints()).isNull();
        assertThat(response.warnings()).containsExactly("ONLY_PUSHES");
    }

    @Test
    void missingImportAddsFreshnessWarningWithNullCompletedAt() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(MatchupReportFixtures.emptyReport(null));
        assertThat(response.dataFreshness().completedAt()).isNull();
        assertThat(response.dataFreshness().importType()).isEqualTo("GAMES_STATS");
        assertThat(response.warnings())
                .containsExactly("NO_QUALIFYING_GAMES", "NO_SUCCESSFUL_GAMES_STATS_IMPORT");
    }
}
