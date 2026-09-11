package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LineTest {

    @Test
    void acceptsZeroAnd999() {
        assertThat(Line.of(BigDecimal.ZERO).value()).isEqualTo(BigDecimal.ZERO);
        assertThat(Line.of(new BigDecimal("999")).value()).isEqualTo(new BigDecimal("999"));
    }

    @Test
    void acceptsScaleUpTo3ThenNormalizes() {
        Line line = Line.of(new BigDecimal("24.500"));
        assertThat(line.value()).isEqualByComparingTo(new BigDecimal("24.5"));
        assertThat(line).isEqualTo(Line.of(new BigDecimal("24.5")));
    }

    @Test
    void rejectsJustOutsideRangeNullAndScale4() {
        assertThatThrownBy(() -> Line.of(new BigDecimal("-0.001")))
                .isInstanceOf(InvalidNumericValueException.class)
                .hasFieldOrPropertyWithValue("field", "line");
        assertThatThrownBy(() -> Line.of(new BigDecimal("999.001")))
                .isInstanceOf(InvalidNumericValueException.class);
        assertThatThrownBy(() -> Line.of(new BigDecimal("1000")))
                .isInstanceOf(InvalidNumericValueException.class);
        assertThatThrownBy(() -> Line.of(null)).isInstanceOf(InvalidNumericValueException.class);
        assertThatThrownBy(() -> Line.of(new BigDecimal("24.5000")))
                .isInstanceOf(InvalidNumericValueException.class)
                .hasFieldOrPropertyWithValue("field", "line");
    }

    @Test
    void numericallyEquivalentValuesHaveSameEqualsAndHashCode() {
        Line integer = Line.of(new BigDecimal("24"));
        Line oneDecimal = Line.of(new BigDecimal("24.0"));
        Line threeDecimals = Line.of(new BigDecimal("24.000"));
        assertThat(integer).isEqualTo(oneDecimal).isEqualTo(threeDecimals);
        assertThat(integer.hashCode()).isEqualTo(oneDecimal.hashCode()).isEqualTo(threeDecimals.hashCode());
        assertThat(integer.value().compareTo(oneDecimal.value())).isZero();
        assertThat(integer.value().compareTo(threeDecimals.value())).isZero();
    }
}
