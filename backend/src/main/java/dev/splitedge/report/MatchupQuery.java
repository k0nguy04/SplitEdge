package dev.splitedge.report;

import java.util.Objects;

public record MatchupQuery(
        long nbaOpponentTeamId,
        PropType prop,
        Line line,
        Direction direction,
        String season,
        LocationFilter location,
        RecencyFilter recency,
        MinMinutes minMinutes) {

    public MatchupQuery {
        Objects.requireNonNull(prop, "prop");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(recency, "recency");
        Objects.requireNonNull(minMinutes, "minMinutes");
    }

    public boolean appliesSeason(String gameSeason) {
        return season == null || season.equals(gameSeason);
    }
}
