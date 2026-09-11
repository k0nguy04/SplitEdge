package dev.splitedge.report;

import java.util.Arrays;
import java.util.List;

/**
 * Static catalog of every prop the calculator supports. The catalog is derived
 * directly from {@link PropType}: the list of entries is built by iterating
 * {@link PropType#values()} in declaration order (so the count and order can never
 * drift from the enum), and every component stat code is itself a base
 * {@link PropType} name (so the components can never name a stat the calculator does
 * not understand). A dedicated test additionally cross-checks that summing the
 * component values via {@link PropValueCalculator} always equals the composite prop's
 * own value, guarding against the two switches ever falling out of sync.
 */
public final class PropCatalog {

    private PropCatalog() {}

    public static List<PropCatalogEntry> entries() {
        return Arrays.stream(PropType.values()).map(PropCatalog::entry).toList();
    }

    private static PropCatalogEntry entry(PropType prop) {
        return new PropCatalogEntry(prop.name(), displayName(prop), componentStats(prop));
    }

    private static String displayName(PropType prop) {
        return switch (prop) {
            case POINTS -> "Points";
            case REBOUNDS -> "Rebounds";
            case ASSISTS -> "Assists";
            case THREE_POINTERS_MADE -> "Three-Pointers Made";
            case PR -> "Points + Rebounds";
            case PA -> "Points + Assists";
            case RA -> "Rebounds + Assists";
            case PRA -> "Points + Rebounds + Assists";
        };
    }

    private static List<String> componentStats(PropType prop) {
        return switch (prop) {
            case POINTS -> List.of(PropType.POINTS.name());
            case REBOUNDS -> List.of(PropType.REBOUNDS.name());
            case ASSISTS -> List.of(PropType.ASSISTS.name());
            case THREE_POINTERS_MADE -> List.of(PropType.THREE_POINTERS_MADE.name());
            case PR -> List.of(PropType.POINTS.name(), PropType.REBOUNDS.name());
            case PA -> List.of(PropType.POINTS.name(), PropType.ASSISTS.name());
            case RA -> List.of(PropType.REBOUNDS.name(), PropType.ASSISTS.name());
            case PRA -> List.of(PropType.POINTS.name(), PropType.REBOUNDS.name(), PropType.ASSISTS.name());
        };
    }
}
