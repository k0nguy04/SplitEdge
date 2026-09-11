package dev.splitedge.report;

import java.math.BigDecimal;

public record Line(BigDecimal value) {

    public static final BigDecimal MIN = BigDecimal.ZERO;
    public static final BigDecimal MAX = new BigDecimal("999");

    public Line {
        value = DecimalBounds.require(value, "line", MIN, MAX);
    }

    public static Line of(BigDecimal value) {
        return new Line(value);
    }
}
