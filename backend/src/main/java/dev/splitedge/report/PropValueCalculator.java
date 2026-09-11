package dev.splitedge.report;

import java.math.BigDecimal;

public final class PropValueCalculator {

    public BigDecimal value(PropType prop, PlayerGameLine game) {
        int raw = switch (prop) {
            case POINTS -> game.points();
            case REBOUNDS -> game.rebounds();
            case ASSISTS -> game.assists();
            case THREE_POINTERS_MADE -> game.threePointersMade();
            case PR -> game.points() + game.rebounds();
            case PA -> game.points() + game.assists();
            case RA -> game.rebounds() + game.assists();
            case PRA -> game.points() + game.rebounds() + game.assists();
        };
        return BigDecimal.valueOf(raw);
    }
}
