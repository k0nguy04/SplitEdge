package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PropValueCalculatorTest {

    private final PropValueCalculator calculator = new PropValueCalculator();
    private final PlayerGameLine game = MatchupFixtures.game(
            "0022400001",
            LocalDate.of(2024, 10, 22),
            GameLocation.HOME,
            MatchupFixtures.BOS,
            "36.000",
            30,
            5,
            8,
            4);

    @Test
    void pointsUsesStoredPoints() {
        assertThat(calculator.value(PropType.POINTS, game)).isEqualByComparingTo(BigDecimal.valueOf(30));
    }

    @Test
    void reboundsUsesStoredRebounds() {
        assertThat(calculator.value(PropType.REBOUNDS, game)).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void assistsUsesStoredAssists() {
        assertThat(calculator.value(PropType.ASSISTS, game)).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    void threePointersMadeUsesStoredThrees() {
        assertThat(calculator.value(PropType.THREE_POINTERS_MADE, game))
                .isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void prUsesStoredPointsPlusRebounds() {
        assertThat(calculator.value(PropType.PR, game)).isEqualByComparingTo(BigDecimal.valueOf(35));
    }

    @Test
    void paUsesStoredPointsPlusAssists() {
        assertThat(calculator.value(PropType.PA, game)).isEqualByComparingTo(BigDecimal.valueOf(38));
    }

    @Test
    void raUsesStoredReboundsPlusAssists() {
        assertThat(calculator.value(PropType.RA, game)).isEqualByComparingTo(BigDecimal.valueOf(13));
    }

    @Test
    void praUsesStoredPointsReboundsAndAssists() {
        assertThat(calculator.value(PropType.PRA, game)).isEqualByComparingTo(BigDecimal.valueOf(43));
    }
}
