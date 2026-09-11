package dev.splitedge.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PlayerGameLine(
        String nbaGameId,
        LocalDate gameDate,
        String season,
        GameLocation location,
        long nbaPlayerId,
        long nbaTeamId,
        long opponentNbaTeamId,
        BigDecimal minutes,
        int points,
        int rebounds,
        int assists,
        int threePointersMade) {

    private static final String GAME_ID_PATTERN = "[0-9]{10}";

    public PlayerGameLine {
        Objects.requireNonNull(nbaGameId, "nbaGameId");
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(minutes, "minutes");
        if (!nbaGameId.matches(GAME_ID_PATTERN)) {
            throw new IllegalArgumentException("nbaGameId must be exactly 10 ASCII digits");
        }
        if (season.isBlank()) {
            throw new IllegalArgumentException("season must not be blank");
        }
        if (nbaPlayerId <= 0) {
            throw new IllegalArgumentException("nbaPlayerId must be positive");
        }
        if (nbaTeamId <= 0) {
            throw new IllegalArgumentException("nbaTeamId must be positive");
        }
        if (opponentNbaTeamId <= 0) {
            throw new IllegalArgumentException("opponentNbaTeamId must be positive");
        }
        if (nbaTeamId == opponentNbaTeamId) {
            throw new IllegalArgumentException("nbaTeamId and opponentNbaTeamId must be different");
        }
        if (minutes.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minutes must not be negative");
        }
        if (points < 0 || rebounds < 0 || assists < 0 || threePointersMade < 0) {
            throw new IllegalArgumentException("box-score counting stats must not be negative");
        }
    }
}
