package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ResultClassifierTest {

    private final ResultClassifier classifier = new ResultClassifier();

    @Test
    void overUsesStrictGreaterThan() {
        Line line = Line.of(new BigDecimal("24.5"));
        assertThat(classifier.classify(new BigDecimal("25"), line, Direction.OVER)).isEqualTo(GameResult.HIT);
        assertThat(classifier.classify(new BigDecimal("24"), line, Direction.OVER)).isEqualTo(GameResult.MISS);
        assertThat(classifier.classify(new BigDecimal("24.5"), line, Direction.OVER)).isEqualTo(GameResult.PUSH);
    }

    @Test
    void underUsesStrictLessThan() {
        Line line = Line.of(new BigDecimal("24.5"));
        assertThat(classifier.classify(new BigDecimal("24"), line, Direction.UNDER)).isEqualTo(GameResult.HIT);
        assertThat(classifier.classify(new BigDecimal("25"), line, Direction.UNDER)).isEqualTo(GameResult.MISS);
        assertThat(classifier.classify(new BigDecimal("24.5"), line, Direction.UNDER)).isEqualTo(GameResult.PUSH);
    }

    @Test
    void numericallyEqualValuesWithDifferentScalesArePushes() {
        Line line = Line.of(new BigDecimal("24.000"));
        assertThat(classifier.classify(new BigDecimal("24"), line, Direction.OVER)).isEqualTo(GameResult.PUSH);
        assertThat(classifier.classify(new BigDecimal("24.0"), line, Direction.UNDER)).isEqualTo(GameResult.PUSH);
        assertThat(classifier.classify(BigDecimal.valueOf(24), line, Direction.OVER)).isEqualTo(GameResult.PUSH);
    }
}
