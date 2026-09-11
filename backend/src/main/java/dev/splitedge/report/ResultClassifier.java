package dev.splitedge.report;

import java.math.BigDecimal;

public final class ResultClassifier {

    public GameResult classify(BigDecimal propValue, Line line, Direction direction) {
        int comparison = propValue.compareTo(line.value());
        if (comparison == 0) {
            return GameResult.PUSH;
        }
        if (direction == Direction.OVER) {
            return comparison > 0 ? GameResult.HIT : GameResult.MISS;
        }
        return comparison < 0 ? GameResult.HIT : GameResult.MISS;
    }
}
