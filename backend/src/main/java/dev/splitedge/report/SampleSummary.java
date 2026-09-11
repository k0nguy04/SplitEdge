package dev.splitedge.report;

import java.math.BigDecimal;

public record SampleSummary(
        int qualifyingGames,
        int hits,
        int misses,
        int pushes,
        ExactFraction hitRate,
        ExactFraction average,
        BigDecimal median) {

    public SampleSummary {
        if (qualifyingGames < 0 || hits < 0 || misses < 0 || pushes < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (qualifyingGames != hits + misses + pushes) {
            throw new IllegalArgumentException("qualifyingGames must equal hits + misses + pushes");
        }
        int decided = hits + misses;
        if (qualifyingGames == 0) {
            if (hitRate != null || average != null || median != null) {
                throw new IllegalArgumentException(
                        "empty samples must have null hitRate, average, and median");
            }
        } else if (average == null || median == null) {
            throw new IllegalArgumentException("nonempty samples must have average and median");
        }
        if (decided == 0) {
            if (hitRate != null) {
                throw new IllegalArgumentException("zero decided games must have a null hitRate");
            }
        } else {
            if (hitRate == null) {
                throw new IllegalArgumentException("positive decided games must have a hitRate");
            }
            if (!hitRate.equals(ExactFraction.of(hits, decided))) {
                throw new IllegalArgumentException("hitRate must equal hits / (hits + misses)");
            }
        }
    }
}
