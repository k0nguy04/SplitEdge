package dev.splitedge.report;

import java.math.BigDecimal;

public record MinMinutes(BigDecimal value) {

    public static final BigDecimal MIN = BigDecimal.ZERO;
    public static final BigDecimal MAX = new BigDecimal("80");

    public MinMinutes {
        value = DecimalBounds.require(value, "minMinutes", MIN, MAX);
    }

    public static MinMinutes of(BigDecimal value) {
        return new MinMinutes(value);
    }
}
