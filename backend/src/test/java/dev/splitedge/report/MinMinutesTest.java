package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MinMinutesTest {

    @Test
    void acceptsZeroTwentyAndEighty() {
        assertThat(MinMinutes.of(BigDecimal.ZERO).value()).isEqualTo(BigDecimal.ZERO);
        assertThat(MinMinutes.of(new BigDecimal("20.000")).value()).isEqualTo(new BigDecimal("20"));
        assertThat(MinMinutes.of(new BigDecimal("80.000")).value()).isEqualTo(new BigDecimal("80"));
    }

    @Test
    void rejectsJustOutsideRangeNullAndScale4() {
        assertThatThrownBy(() -> MinMinutes.of(new BigDecimal("-0.001")))
                .isInstanceOf(InvalidNumericValueException.class)
                .hasFieldOrPropertyWithValue("field", "minMinutes");
        assertThatThrownBy(() -> MinMinutes.of(new BigDecimal("80.001")))
                .isInstanceOf(InvalidNumericValueException.class);
        assertThatThrownBy(() -> MinMinutes.of(null)).isInstanceOf(InvalidNumericValueException.class);
        assertThatThrownBy(() -> MinMinutes.of(new BigDecimal("20.0000")))
                .isInstanceOf(InvalidNumericValueException.class);
    }

    @Test
    void numericallyEquivalentValuesHaveSameEqualsAndHashCode() {
        MinMinutes integer = MinMinutes.of(new BigDecimal("20"));
        MinMinutes oneDecimal = MinMinutes.of(new BigDecimal("20.0"));
        MinMinutes threeDecimals = MinMinutes.of(new BigDecimal("20.000"));
        assertThat(integer).isEqualTo(oneDecimal).isEqualTo(threeDecimals);
        assertThat(integer.hashCode())
                .isEqualTo(oneDecimal.hashCode())
                .isEqualTo(threeDecimals.hashCode());
    }
}
