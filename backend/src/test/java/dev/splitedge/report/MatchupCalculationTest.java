package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MatchupCalculationTest {

    @Test
    void defensivelyCopiesMatchupGamesAndExposesAnUnmodifiableList() {
        PlayerGameLine row = MatchupFixtures.eightGameSeason().getFirst();
        ClassifiedGame classified = new ClassifiedGame(row, BigDecimal.valueOf(30), GameResult.HIT);
        List<ClassifiedGame> mutable = new ArrayList<>();
        mutable.add(classified);
        SampleSummary summary = new SampleSummary(
                1, 1, 0, 0, ExactFraction.of(1, 1), ExactFraction.of(30, 1), BigDecimal.valueOf(30));
        MatchupCalculation calculation =
                new MatchupCalculation(summary, summary, SampleQuality.LOW, ExactFraction.of(0, 1), mutable);
        mutable.clear();
        assertThat(calculation.matchupGames()).containsExactly(classified);
        assertThatThrownBy(() -> calculation.matchupGames().removeFirst())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
