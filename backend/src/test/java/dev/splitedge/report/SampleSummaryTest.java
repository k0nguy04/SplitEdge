package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SampleSummaryTest {

    @Test
    void emptySampleRequiresNullRateAverageAndMedian() {
        SampleSummary empty = new SampleSummary(0, 0, 0, 0, null, null, null);
        assertThat(empty.hitRate()).isNull();
        assertThat(empty.average()).isNull();
        assertThat(empty.median()).isNull();
        assertThatThrownBy(() -> new SampleSummary(0, 0, 0, 0, ExactFraction.of(0, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(0, 0, 0, 0, null, ExactFraction.of(0, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SampleSummary(0, 0, 0, 0, null, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonemptyAndOnlyPushSamplesEnforceRateAverageAndCountIdentity() {
        SampleSummary onlyPushes = new SampleSummary(
                2, 0, 0, 2, null, ExactFraction.of(48, 2), new BigDecimal("24"));
        assertThat(onlyPushes.hitRate()).isNull();
        assertThat(onlyPushes.average()).isEqualTo(ExactFraction.of(24, 1));
        SampleSummary decided = new SampleSummary(
                3, 1, 2, 0, ExactFraction.of(1, 3), ExactFraction.of(70, 3), new BigDecimal("20"));
        assertThat(decided.hitRate()).isEqualTo(new ExactFraction(1, 3));
        assertThatThrownBy(
                        () -> new SampleSummary(3, 1, 1, 0, ExactFraction.of(1, 2), ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, 1, 0, 0, null, ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, 0, 0, 1, ExactFraction.of(0, 1), ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, 1, 0, 0, ExactFraction.of(1, 1), null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, 1, 0, 0, ExactFraction.of(1, 1), ExactFraction.of(1, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(2, 1, 0, 0, ExactFraction.of(1, 1), ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, -1, 1, 1, ExactFraction.of(0, 1), ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SampleSummary(1, 1, 0, 0, ExactFraction.of(2, 3), ExactFraction.of(1, 1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
