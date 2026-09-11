package dev.splitedge.report;

public final class InconsistentPlayerAppearanceException extends IllegalStateException {

    public InconsistentPlayerAppearanceException(
            String nbaGameId, long nbaTeamId, long homeNbaTeamId, long awayNbaTeamId) {
        super("player game-night team "
                + nbaTeamId
                + " in game "
                + nbaGameId
                + " matches neither home "
                + homeNbaTeamId
                + " nor away "
                + awayNbaTeamId);
    }
}
