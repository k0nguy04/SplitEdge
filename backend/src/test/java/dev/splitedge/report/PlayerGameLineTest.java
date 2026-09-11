package dev.splitedge.report;

import static dev.splitedge.report.MatchupFixtures.BOS;
import static dev.splitedge.report.MatchupFixtures.GSW;
import static dev.splitedge.report.MatchupFixtures.PLAYER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PlayerGameLineTest {

    private static final LocalDate DATE = LocalDate.of(2024, 10, 22);

    @Test
    void acceptsValidRowIncludingMinutesAboveFilterMaximum() {
        PlayerGameLine row = new PlayerGameLine(
                "0022400001",
                DATE,
                "2024-25",
                GameLocation.HOME,
                PLAYER,
                GSW,
                BOS,
                new BigDecimal("90.000"),
                0,
                0,
                0,
                0);
        assertThat(row.minutes()).isEqualByComparingTo("90.000");
        assertThat(row.nbaPlayerId()).isEqualTo(PLAYER);
        PlayerGameLine zeroMinutes = new PlayerGameLine(
                "0022400002",
                DATE,
                "2024-25",
                GameLocation.AWAY,
                PLAYER,
                GSW,
                BOS,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0);
        assertThat(zeroMinutes.minutes()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsBlankOrMalformedGameId() {
        assertThatThrownBy(() -> line(null, DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> line("", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("22400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> line("0022400001a", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> line("002240000 ", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSeasonAndNullRequiredFields() {
        assertThatThrownBy(() -> line("0022400001", DATE, "  ", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, null, GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> line("0022400001", null, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", null, PLAYER, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new PlayerGameLine(
                                "0022400001",
                                DATE,
                                "2024-25",
                                GameLocation.HOME,
                                PLAYER,
                                GSW,
                                BOS,
                                null,
                                1,
                                1,
                                1,
                                1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveIdsAndMatchingTeams() {
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, 0, GSW, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, 0, BOS, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, -1, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, GSW, "36", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMinutesAndCountingStats() {
        assertThatThrownBy(
                        () -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "-0.001", 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", -1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("0022400001", DATE, "2024-25", GameLocation.HOME, PLAYER, GSW, BOS, "36", 0, 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PlayerGameLine line(
            String nbaGameId,
            LocalDate date,
            String season,
            GameLocation location,
            long playerId,
            long teamId,
            long opponentId,
            String minutes,
            int points,
            int rebounds,
            int assists,
            int threes) {
        return new PlayerGameLine(
                nbaGameId,
                date,
                season,
                location,
                playerId,
                teamId,
                opponentId,
                minutes == null ? null : new BigDecimal(minutes),
                points,
                rebounds,
                assists,
                threes);
    }
}
