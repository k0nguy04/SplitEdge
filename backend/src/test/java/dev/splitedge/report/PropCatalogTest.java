package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PropCatalogTest {

    @Test
    void hasExactlyEightEntriesInDeclarationOrder() {
        assertThat(PropCatalog.entries())
                .extracting(PropCatalogEntry::code)
                .containsExactly(
                        "POINTS", "REBOUNDS", "ASSISTS", "THREE_POINTERS_MADE", "PR", "PA", "RA", "PRA");
    }

    @Test
    void everyCodeMatchesAPropTypeName() {
        for (PropCatalogEntry entry : PropCatalog.entries()) {
            assertThat(PropType.valueOf(entry.code())).isNotNull();
        }
    }

    @Test
    void everyComponentStatCodeMatchesAPropTypeName() {
        for (PropCatalogEntry entry : PropCatalog.entries()) {
            for (String component : entry.componentStats()) {
                assertThat(PropType.valueOf(component)).isNotNull();
            }
        }
    }

    @Test
    void baseStatsHaveExactlyOneComponentEqualToThemselves() {
        assertThat(componentsOf("POINTS")).containsExactly("POINTS");
        assertThat(componentsOf("REBOUNDS")).containsExactly("REBOUNDS");
        assertThat(componentsOf("ASSISTS")).containsExactly("ASSISTS");
        assertThat(componentsOf("THREE_POINTERS_MADE")).containsExactly("THREE_POINTERS_MADE");
    }

    @Test
    void compositeStatsListEveryUnderlyingComponent() {
        assertThat(componentsOf("PR")).containsExactly("POINTS", "REBOUNDS");
        assertThat(componentsOf("PA")).containsExactly("POINTS", "ASSISTS");
        assertThat(componentsOf("RA")).containsExactly("REBOUNDS", "ASSISTS");
        assertThat(componentsOf("PRA")).containsExactly("POINTS", "REBOUNDS", "ASSISTS");
    }

    @Test
    void displayNamesAreNonBlankAndDistinct() {
        assertThat(PropCatalog.entries())
                .extracting(PropCatalogEntry::displayName)
                .doesNotHaveDuplicates()
                .allSatisfy(name -> assertThat(name).isNotBlank());
    }

    /**
     * The catalog's component-stat lists must never drift from what the calculator
     * actually sums: for every composite prop, summing the calculator's value for each
     * listed component must equal the calculator's own value for the composite prop.
     */
    @Test
    void componentStatsNeverDriftFromWhatThePropValueCalculatorSums() {
        PlayerGameLine game = new PlayerGameLine(
                "0022400001",
                LocalDate.of(2024, 11, 1),
                "2024-25",
                GameLocation.HOME,
                1L,
                2L,
                3L,
                new BigDecimal("34.000"),
                21,
                7,
                5,
                3);
        PropValueCalculator calculator = new PropValueCalculator();

        for (PropCatalogEntry entry : PropCatalog.entries()) {
            PropType prop = PropType.valueOf(entry.code());
            BigDecimal expected = calculator.value(prop, game);
            BigDecimal sumOfComponents = entry.componentStats().stream()
                    .map(PropType::valueOf)
                    .map(component -> calculator.value(component, game))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumOfComponents).as("components of %s", entry.code()).isEqualByComparingTo(expected);
        }
    }

    private static java.util.List<String> componentsOf(String code) {
        return PropCatalog.entries().stream()
                .filter(entry -> entry.code().equals(code))
                .findFirst()
                .orElseThrow()
                .componentStats();
    }
}
