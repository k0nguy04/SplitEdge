package dev.splitedge.report;

import java.util.List;

/**
 * One entry in the static supported-props catalog. {@code componentStats} always
 * contains one or more {@link PropType} names (the base stats that are summed to
 * produce this prop's value).
 */
public record PropCatalogEntry(String code, String displayName, List<String> componentStats) {

    public PropCatalogEntry {
        componentStats = List.copyOf(componentStats);
    }
}
