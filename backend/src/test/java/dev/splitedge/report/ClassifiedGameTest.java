package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ClassifiedGameTest {

    @Test
    void rejectsNullComponents() {
        PlayerGameLine game = MatchupFixtures.eightGameSeason().getFirst();
        assertThatThrownBy(() -> new ClassifiedGame(null, BigDecimal.ONE, GameResult.HIT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClassifiedGame(game, null, GameResult.HIT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClassifiedGame(game, BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
