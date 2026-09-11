package dev.splitedge.report;

import java.math.BigDecimal;

final class DecimalBounds {

    private DecimalBounds() {}

    static BigDecimal require(BigDecimal value, String field, BigDecimal min, BigDecimal max) {
        if (value == null) {
            throw new InvalidNumericValueException(field, field + " is required");
        }
        if (value.scale() > 3) {
            throw new InvalidNumericValueException(
                    field, field + " allows at most 3 decimal places and is never rounded");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new InvalidNumericValueException(
                    field, field + " must be between " + min.toPlainString() + " and " + max.toPlainString());
        }
        return normalize(value);
    }

    static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            return stripped.setScale(0);
        }
        return stripped;
    }
}
