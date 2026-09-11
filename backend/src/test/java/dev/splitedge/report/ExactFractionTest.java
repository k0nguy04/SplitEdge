package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExactFractionTest {

    @Test
    void reducesByGreatestCommonDivisorAndNormalizesSign() {
        assertThat(ExactFraction.of(2, 4)).isEqualTo(new ExactFraction(1, 2));
        assertThat(ExactFraction.of(-2, -4)).isEqualTo(new ExactFraction(1, 2));
        assertThat(ExactFraction.of(1, -3)).isEqualTo(new ExactFraction(-1, 3));
        assertThat(ExactFraction.of(0, 5)).isEqualTo(new ExactFraction(0, 1));
        assertThat(ExactFraction.of(1, 3).hashCode()).isEqualTo(new ExactFraction(1, 3).hashCode());
        assertThat(ExactFraction.of(2, 4).hashCode()).isEqualTo(ExactFraction.of(1, 2).hashCode());
    }

    @Test
    void rejectsZeroDenominator() {
        assertThatThrownBy(() -> ExactFraction.of(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExactFraction(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtractsAndMultipliesWithoutDecimalRounding() {
        ExactFraction difference = ExactFraction.of(1, 4).subtract(ExactFraction.of(3, 8));
        assertThat(difference).isEqualTo(ExactFraction.of(-1, 8));
        assertThat(difference.multiply(100)).isEqualTo(ExactFraction.of(-25, 2));
        assertThat(ExactFraction.of(1, 3).multiply(100)).isEqualTo(ExactFraction.of(100, 3));
    }
}
