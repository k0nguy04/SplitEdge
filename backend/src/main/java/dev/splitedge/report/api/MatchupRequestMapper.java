package dev.splitedge.report.api;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import dev.splitedge.report.Direction;
import dev.splitedge.report.InvalidNumericValueException;
import dev.splitedge.report.Line;
import dev.splitedge.report.LocationFilter;
import dev.splitedge.report.MatchupReportCommand;
import dev.splitedge.report.MinMinutes;
import dev.splitedge.report.PropType;
import dev.splitedge.report.RecencyFilter;
import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

/**
 * Converts a structurally parsed request into domain types. Enum names are matched
 * exactly and case-sensitively; numeric values are never rounded or coerced.
 */
public final class MatchupRequestMapper {

    private static final Pattern SEASON = Pattern.compile("^\\d{4}-\\d{2}$");

    private MatchupRequestMapper() {}

    public static MatchupReportCommand toCommand(MatchupReportRequest request) {
        return new MatchupReportCommand(
                request.nbaPlayerId(),
                request.nbaOpponentTeamId(),
                prop(request.prop()),
                line(request.line()),
                direction(request.direction()),
                season(request.season()),
                location(request.location()),
                recency(request.recency()),
                minMinutes(request.minMinutes()));
    }

    private static PropType prop(String value) {
        for (PropType prop : PropType.values()) {
            if (prop.name().equals(value)) {
                return prop;
            }
        }
        throw ApiException.field(
                ApiErrorCode.UNSUPPORTED_PROP,
                "prop",
                "prop must be one of POINTS, REBOUNDS, ASSISTS, THREE_POINTERS_MADE, PR, PA, RA, PRA");
    }

    private static Direction direction(String value) {
        for (Direction direction : Direction.values()) {
            if (direction.name().equals(value)) {
                return direction;
            }
        }
        throw ApiException.field(
                ApiErrorCode.INVALID_DIRECTION, "direction", "direction must be OVER or UNDER");
    }

    private static LocationFilter location(String value) {
        if (value == null) {
            return LocationFilter.ALL;
        }
        for (LocationFilter location : LocationFilter.values()) {
            if (location.name().equals(value)) {
                return location;
            }
        }
        throw ApiException.field(
                ApiErrorCode.INVALID_LOCATION, "location", "location must be HOME, AWAY, or ALL");
    }

    private static RecencyFilter recency(String value) {
        if (value == null) {
            return RecencyFilter.ALL;
        }
        for (RecencyFilter recency : RecencyFilter.values()) {
            if (recency.name().equals(value)) {
                return recency;
            }
        }
        throw ApiException.field(
                ApiErrorCode.INVALID_RECENCY, "recency", "recency must be LAST_5, LAST_10, LAST_20, or ALL");
    }

    private static Line line(BigDecimal value) {
        try {
            return Line.of(value);
        } catch (InvalidNumericValueException ex) {
            throw ApiException.field(
                    ApiErrorCode.INVALID_LINE,
                    "line",
                    "line must be a JSON number from 0 through 999 with at most 3 decimal places");
        }
    }

    private static MinMinutes minMinutes(BigDecimal value) {
        try {
            return MinMinutes.of(value == null ? BigDecimal.ZERO : value);
        } catch (InvalidNumericValueException ex) {
            throw ApiException.field(
                    ApiErrorCode.INVALID_MIN_MINUTES,
                    "minMinutes",
                    "minMinutes must be a JSON number from 0 through 80 with at most 3 decimal places");
        }
    }

    private static String season(String value) {
        if (value == null) {
            return null;
        }
        if (!SEASON.matcher(value).matches()) {
            throw ApiException.field(
                    ApiErrorCode.INVALID_SEASON, "season", "season must match YYYY-YY, for example 2024-25");
        }
        return value;
    }
}
