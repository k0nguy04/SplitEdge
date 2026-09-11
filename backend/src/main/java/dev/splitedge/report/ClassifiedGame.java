package dev.splitedge.report;

import java.math.BigDecimal;
import java.util.Objects;

public record ClassifiedGame(PlayerGameLine game, BigDecimal propValue, GameResult result) {

    public ClassifiedGame {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(propValue, "propValue");
        Objects.requireNonNull(result, "result");
    }
}
