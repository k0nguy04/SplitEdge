package dev.splitedge.report;

import static dev.splitedge.report.MatchupFixtures.BOS;
import static dev.splitedge.report.MatchupFixtures.currentSeasonThenRecentPriorSeason;
import static dev.splitedge.report.MatchupFixtures.eightGameSeason;
import static dev.splitedge.report.MatchupFixtures.exactMinutesBoundary;
import static dev.splitedge.report.MatchupFixtures.game;
import static dev.splitedge.report.MatchupFixtures.highMinutesThenRecentLowMinutes;
import static dev.splitedge.report.MatchupFixtures.homeGamesThenRecentAwayGames;
import static dev.splitedge.report.MatchupFixtures.oneHitTwoMissesVsBos;
import static dev.splitedge.report.MatchupFixtures.onlyPushLast5;
import static dev.splitedge.report.MatchupFixtures.pointsOverBos;
import static dev.splitedge.report.MatchupFixtures.sameDateTieBreak;
import static dev.splitedge.report.MatchupFixtures.sequentialVsBos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MatchupCalculatorTest {

    private final MatchupCalculator calculator = new MatchupCalculator();

    @Test
    void overDecimalLineExcludesPushesFromRateAndIncludesThemInAverageMedian() {
        MatchupCalculation result = calculator.calculate(
                eightGameSeason(), pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        SampleSummary matchup = result.matchup();
        assertThat(matchup.qualifyingGames()).isEqualTo(4);
        assertThat(matchup.hits()).isEqualTo(1);
        assertThat(matchup.misses()).isEqualTo(3);
        assertThat(matchup.pushes()).isEqualTo(0);
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(1, 4));
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(90, 4));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("24"));
        assertThat(result.sampleQuality()).isEqualTo(SampleQuality.LOW);
        assertThat(result.baseline().qualifyingGames()).isEqualTo(8);
        assertThat(result.baseline().hits()).isEqualTo(3);
        assertThat(result.baseline().misses()).isEqualTo(5);
        assertThat(result.baseline().hitRate()).isEqualTo(ExactFraction.of(3, 8));
        assertThat(result.hitRateDifferencePoints()).isEqualTo(ExactFraction.of(-25, 2));
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400001", "0022400003", "0022400004", "0022400006");
    }

    @Test
    void underHitsWhenValueIsStrictlyBelowTheLine() {
        MatchupQuery query = new MatchupQuery(
                BOS,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.UNDER,
                "2024-25",
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
        SampleSummary matchup = calculator.calculate(eightGameSeason(), query).matchup();
        assertThat(matchup.hits()).isEqualTo(3);
        assertThat(matchup.misses()).isEqualTo(1);
        assertThat(matchup.pushes()).isEqualTo(0);
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(3, 4));
    }

    @Test
    void pushCountIsExcludedFromDenominatorButIncludedInQualifyingAverageAndMedian() {
        MatchupCalculation result = calculator.calculate(
                eightGameSeason(), pointsOverBos(24, RecencyFilter.ALL, 0));
        SampleSummary matchup = result.matchup();
        assertThat(matchup.hits()).isEqualTo(1);
        assertThat(matchup.misses()).isEqualTo(1);
        assertThat(matchup.pushes()).isEqualTo(2);
        assertThat(matchup.qualifyingGames()).isEqualTo(4);
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(1, 2));
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(90, 4));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("24"));
    }

    @Test
    void prUsesStoredPointsPlusRebounds() {
        assertPropHits(PropType.PR, new BigDecimal("29.5"), 3, 1);
    }

    @Test
    void paUsesStoredPointsPlusAssists() {
        assertPropHits(PropType.PA, new BigDecimal("29.5"), 3, 1);
    }

    @Test
    void raUsesStoredReboundsPlusAssists() {
        assertPropHits(PropType.RA, new BigDecimal("10.5"), 3, 1);
    }

    @Test
    void praUsesStoredPointsReboundsAndAssists() {
        assertPropHits(PropType.PRA, new BigDecimal("36.5"), 3, 1);
    }

    @Test
    void eachIndividualPropUsesItsStoredStat() {
        PlayerGameLine row = eightGameSeason().getFirst();
        assertThat(new PropValueCalculator().value(PropType.POINTS, row)).isEqualByComparingTo("30");
        assertThat(new PropValueCalculator().value(PropType.REBOUNDS, row)).isEqualByComparingTo("5");
        assertThat(new PropValueCalculator().value(PropType.ASSISTS, row)).isEqualByComparingTo("8");
        assertThat(new PropValueCalculator().value(PropType.THREE_POINTERS_MADE, row))
                .isEqualByComparingTo("4");
        SampleSummary threes = calculator.calculate(
                        List.of(row),
                        new MatchupQuery(
                                BOS,
                                PropType.THREE_POINTERS_MADE,
                                Line.of(new BigDecimal("3.5")),
                                Direction.OVER,
                                "2024-25",
                                LocationFilter.ALL,
                                RecencyFilter.ALL,
                                MinMinutes.of(BigDecimal.ZERO)))
                .matchup();
        assertThat(threes.hits()).isEqualTo(1);
        assertThat(threes.misses()).isEqualTo(0);
    }

    @Test
    void emptySampleReturnsNullRatesAverageMedianAndQuality() {
        MatchupCalculation result = calculator.calculate(
                List.of(), pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        assertThat(result.matchup().qualifyingGames()).isZero();
        assertThat(result.matchup().hitRate()).isNull();
        assertThat(result.matchup().average()).isNull();
        assertThat(result.matchup().median()).isNull();
        assertThat(result.baseline().hitRate()).isNull();
        assertThat(result.sampleQuality()).isNull();
        assertThat(result.hitRateDifferencePoints()).isNull();
        assertThat(result.matchupGames()).isEmpty();
    }

    @Test
    void last5OnlyPushSampleReturnsNullHitRateAndExactBaseline() {
        MatchupCalculation result = calculator.calculate(
                onlyPushLast5(), pointsOverBos(24, RecencyFilter.LAST_5, 0));
        SampleSummary matchup = result.matchup();
        assertThat(matchup.qualifyingGames()).isEqualTo(5);
        assertThat(matchup.hits()).isZero();
        assertThat(matchup.misses()).isZero();
        assertThat(matchup.pushes()).isEqualTo(5);
        assertThat(matchup.hitRate()).isNull();
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(120, 5));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("24"));
        assertThat(result.sampleQuality()).isEqualTo(SampleQuality.MODERATE);
        assertThat(result.hitRateDifferencePoints()).isNull();
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400011", "0022400012", "0022400013", "0022400014", "0022400015")
                .doesNotContain("0022400010");
        SampleSummary baseline = result.baseline();
        assertThat(baseline.qualifyingGames()).isEqualTo(5);
        assertThat(baseline.hits()).isZero();
        assertThat(baseline.misses()).isEqualTo(1);
        assertThat(baseline.pushes()).isEqualTo(4);
        assertThat(baseline.hitRate()).isEqualTo(ExactFraction.of(0, 1));
        assertThat(baseline.average()).isEqualTo(ExactFraction.of(116, 5));
        assertThat(baseline.median()).isEqualByComparingTo(new BigDecimal("24"));
    }

    @Test
    void differenceIsNullWhenEitherRateIsNull() {
        MatchupCalculation onlyPushMatchup = calculator.calculate(
                onlyPushLast5(), pointsOverBos(24, RecencyFilter.LAST_5, 0));
        assertThat(onlyPushMatchup.matchup().hitRate()).isNull();
        assertThat(onlyPushMatchup.baseline().hitRate()).isNotNull();
        assertThat(onlyPushMatchup.hitRateDifferencePoints()).isNull();

        MatchupCalculation empty = calculator.calculate(
                List.of(), pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        assertThat(empty.matchup().hitRate()).isNull();
        assertThat(empty.baseline().hitRate()).isNull();
        assertThat(empty.hitRateDifferencePoints()).isNull();
    }

    @Test
    void homeAndAwayFiltersSelectGameNightLocation() {
        MatchupCalculation home = calculator.calculate(
                eightGameSeason(),
                pointsOverBos(new BigDecimal("24.5"), LocationFilter.HOME, RecencyFilter.ALL, 0));
        assertThat(home.matchup().qualifyingGames()).isEqualTo(2);
        assertThat(home.matchup().hits()).isEqualTo(1);
        assertThat(home.matchup().misses()).isEqualTo(1);

        MatchupCalculation away = calculator.calculate(
                eightGameSeason(),
                pointsOverBos(new BigDecimal("24.5"), LocationFilter.AWAY, RecencyFilter.ALL, 0));
        assertThat(away.matchup().qualifyingGames()).isEqualTo(2);
        assertThat(away.matchup().hits()).isZero();
    }

    @Test
    void exactMinimumMinutesAreIncludedAndJustBelowIsExcluded() {
        MatchupCalculation result = calculator.calculate(
                exactMinutesBoundary(),
                pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, new BigDecimal("20.000")));
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400201", "0022400202")
                .doesNotContain("0022400200");
        assertThat(result.matchup().qualifyingGames()).isEqualTo(2);
        assertThat(result.matchup().hits()).isEqualTo(1);
        assertThat(result.matchup().misses()).isEqualTo(1);
    }

    @Test
    void seasonFilterIsAppliedBeforeLast5() {
        MatchupCalculation result = calculator.calculate(
                currentSeasonThenRecentPriorSeason(),
                pointsOverBos(new BigDecimal("24.5"), RecencyFilter.LAST_5, 0));
        assertThat(result.matchup().qualifyingGames()).isEqualTo(5);
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400201", "0022400202", "0022400203", "0022400204", "0022400205")
                .doesNotContain("0022300206");
    }

    @Test
    void locationFilterIsAppliedBeforeLast5() {
        MatchupCalculation result = calculator.calculate(
                homeGamesThenRecentAwayGames(),
                pointsOverBos(new BigDecimal("24.5"), LocationFilter.HOME, RecencyFilter.LAST_5, 0));
        assertThat(result.matchup().qualifyingGames()).isEqualTo(5);
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400001", "0022400002", "0022400003", "0022400004", "0022400005")
                .doesNotContain("0022400010");
    }

    @Test
    void minimumMinutesFilterIsAppliedBeforeLast5() {
        MatchupCalculation result = calculator.calculate(
                highMinutesThenRecentLowMinutes(),
                pointsOverBos(new BigDecimal("24.5"), RecencyFilter.LAST_5, 20));
        assertThat(result.matchup().qualifyingGames()).isEqualTo(5);
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400101", "0022400102", "0022400103", "0022400104", "0022400105")
                .doesNotContain("0022400110");
    }

    @Test
    void sameDateUsesZeroPaddedGameIdDescendingForRecency() {
        MatchupCalculation last5 = calculator.calculate(
                sameDateTieBreak(), pointsOverBos(0, RecencyFilter.LAST_5, 0));
        assertThat(last5.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly("0022400002", "0022400003", "0022400004", "0022400005", "0022400006")
                .doesNotContain("0022400001");
    }

    @Test
    void last10TruncatesASampleLargerThan10() {
        assertThat(calculator.calculate(sequentialVsBos(12), pointsOverBos(0, RecencyFilter.LAST_10, 0))
                        .matchup()
                        .qualifyingGames())
                .isEqualTo(10);
        assertThat(calculator.calculate(sequentialVsBos(12), pointsOverBos(0, RecencyFilter.ALL, 0))
                        .matchup()
                        .qualifyingGames())
                .isEqualTo(12);
    }

    @Test
    void last20TruncatesASampleLargerThan20() {
        assertThat(calculator.calculate(sequentialVsBos(22), pointsOverBos(0, RecencyFilter.LAST_20, 0))
                        .matchup()
                        .qualifyingGames())
                .isEqualTo(20);
        assertThat(calculator.calculate(sequentialVsBos(22), pointsOverBos(0, RecencyFilter.ALL, 0))
                        .matchup()
                        .qualifyingGames())
                .isEqualTo(22);
    }

    @Test
    void recencyIsIndependentForMatchupAndBaseline() {
        MatchupCalculation result = calculator.calculate(
                eightGameSeason(),
                pointsOverBos(new BigDecimal("24.5"), RecencyFilter.LAST_5, 0));
        assertThat(result.matchup().qualifyingGames()).isEqualTo(4);
        assertThat(result.baseline().qualifyingGames()).isEqualTo(5);
        assertThat(result.baseline().hits()).isEqualTo(2);
        assertThat(result.baseline().misses()).isEqualTo(3);
        assertThat(result.baseline().hitRate()).isEqualTo(ExactFraction.of(2, 5));
    }

    @Test
    void oddAndEvenMediansDivideEvenPairsExactlyByTwo() {
        assertThat(MatchupCalculator.median(List.of(
                        BigDecimal.valueOf(12), BigDecimal.valueOf(24), BigDecimal.valueOf(30))))
                .isEqualByComparingTo(new BigDecimal("24"));
        assertThat(MatchupCalculator.median(List.of(
                        BigDecimal.valueOf(12),
                        BigDecimal.valueOf(24),
                        BigDecimal.valueOf(24),
                        BigDecimal.valueOf(30))))
                .isEqualByComparingTo(new BigDecimal("24"));
        assertThat(MatchupCalculator.median(List.of(BigDecimal.valueOf(11), BigDecimal.valueOf(12))))
                .isEqualByComparingTo(new BigDecimal("11.5"));
    }

    @Test
    void oneThirdHitRateAndRepeatingAverageRemainExactInsideTheCalculation() {
        MatchupCalculation result = calculator.calculate(
                oneHitTwoMissesVsBos(), pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        SampleSummary matchup = result.matchup();
        assertThat(matchup.hits()).isEqualTo(1);
        assertThat(matchup.misses()).isEqualTo(2);
        assertThat(matchup.pushes()).isZero();
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(1, 3));
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(70, 3));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(result.hitRateDifferencePoints()).isEqualTo(ExactFraction.of(0, 1));
    }

    @Test
    void sampleQualityUsesMatchupQualifyingCountIncludingPushes() {
        assertThat(calculator.calculate(sequentialVsBos(4), pointsOverBos(100, RecencyFilter.ALL, 0))
                        .sampleQuality())
                .isEqualTo(SampleQuality.LOW);
        assertThat(calculator.calculate(sequentialVsBos(5), pointsOverBos(100, RecencyFilter.ALL, 0))
                        .sampleQuality())
                .isEqualTo(SampleQuality.MODERATE);
        assertThat(calculator.calculate(sequentialVsBos(10), pointsOverBos(100, RecencyFilter.ALL, 0))
                        .sampleQuality())
                .isEqualTo(SampleQuality.HIGH);
    }

    @Test
    void seasonFilterDoesNotSubstituteAnotherSeason() {
        List<PlayerGameLine> games = new ArrayList<>(eightGameSeason());
        games.add(game(
                "0022300999",
                LocalDate.of(2023, 12, 1),
                "2023-24",
                GameLocation.HOME,
                BOS,
                "30.000",
                40,
                10,
                10,
                6));
        MatchupCalculation filtered = calculator.calculate(
                games, pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        assertThat(filtered.matchup().qualifyingGames()).isEqualTo(4);
        MatchupQuery allSeasons = new MatchupQuery(
                BOS,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                null,
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
        assertThat(calculator.calculate(games, allSeasons).matchup().qualifyingGames()).isEqualTo(5);
    }

    @Test
    void recencyFilterAllowsOnlyApprovedValues() {
        assertThat(RecencyFilter.values())
                .containsExactly(RecencyFilter.LAST_5, RecencyFilter.LAST_10, RecencyFilter.LAST_20, RecencyFilter.ALL);
    }

    @Test
    void returnedMatchupGamesListIsUnmodifiable() {
        MatchupCalculation result = calculator.calculate(
                eightGameSeason(), pointsOverBos(new BigDecimal("24.5"), RecencyFilter.ALL, 0));
        assertThatThrownBy(() -> result.matchupGames().add(result.matchupGames().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.matchupGames().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertPropHits(PropType prop, BigDecimal line, int hits, int misses) {
        MatchupQuery query = new MatchupQuery(
                BOS,
                prop,
                Line.of(line),
                Direction.OVER,
                "2024-25",
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
        SampleSummary matchup = calculator.calculate(eightGameSeason(), query).matchup();
        assertThat(matchup.hits()).isEqualTo(hits);
        assertThat(matchup.misses()).isEqualTo(misses);
        assertThat(matchup.pushes()).isZero();
    }
}
