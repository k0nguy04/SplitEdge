package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SampleQualityCalculatorTest {

    private final SampleQualityCalculator calculator = new SampleQualityCalculator();

    @Test
    void labelsMatchupSampleSizeDeterministically() {
        assertThat(calculator.fromQualifyingGames(0)).isNull();
        assertThat(calculator.fromQualifyingGames(1)).isEqualTo(SampleQuality.LOW);
        assertThat(calculator.fromQualifyingGames(4)).isEqualTo(SampleQuality.LOW);
        assertThat(calculator.fromQualifyingGames(5)).isEqualTo(SampleQuality.MODERATE);
        assertThat(calculator.fromQualifyingGames(9)).isEqualTo(SampleQuality.MODERATE);
        assertThat(calculator.fromQualifyingGames(10)).isEqualTo(SampleQuality.HIGH);
        assertThat(calculator.fromQualifyingGames(20)).isEqualTo(SampleQuality.HIGH);
    }
}
