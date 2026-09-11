package dev.splitedge.report;

import java.util.Objects;

/**
 * A structurally valid report request. Season is null when every stored season applies.
 */
public record MatchupReportCommand(
        long nbaPlayerId,
        long nbaOpponentTeamId,
        PropType prop,
        Line line,
        Direction direction,
        String season,
        LocationFilter location,
        RecencyFilter recency,
        MinMinutes minMinutes) {

    public MatchupReportCommand {
        Objects.requireNonNull(prop, "prop");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(recency, "recency");
        Objects.requireNonNull(minMinutes, "minMinutes");
    }

    public MatchupQuery toQuery() {
        return new MatchupQuery(
                nbaOpponentTeamId, prop, line, direction, season, location, recency, minMinutes);
    }
}
