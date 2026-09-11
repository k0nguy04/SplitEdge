package dev.splitedge.report;

import java.util.OptionalInt;

public enum RecencyFilter {
    LAST_5(5),
    LAST_10(10),
    LAST_20(20),
    ALL(null);

    private final Integer limit;

    RecencyFilter(Integer limit) {
        this.limit = limit;
    }

    public OptionalInt limit() {
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }
}
