package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import dev.splitedge.report.Direction;
import dev.splitedge.report.LocationFilter;
import dev.splitedge.report.MatchupReportCommand;
import dev.splitedge.report.PropType;
import dev.splitedge.report.RecencyFilter;
import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

class MatchupRequestMapperTest {

    @Test
    void appliesDocumentedDefaultsForOptionalFields() {
        MatchupReportCommand command = MatchupRequestMapper.toCommand(request(
                "POINTS", new BigDecimal("24.5"), "OVER", null, null, null, null));
        assertThat(command.nbaPlayerId()).isEqualTo(201_939L);
        assertThat(command.nbaOpponentTeamId()).isEqualTo(1_610_612_738L);
        assertThat(command.prop()).isEqualTo(PropType.POINTS);
        assertThat(command.direction()).isEqualTo(Direction.OVER);
        assertThat(command.season()).isNull();
        assertThat(command.location()).isEqualTo(LocationFilter.ALL);
        assertThat(command.recency()).isEqualTo(RecencyFilter.ALL);
        assertThat(command.minMinutes().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(command.line().value()).isEqualByComparingTo(new BigDecimal("24.5"));
    }

    @Test
    void mapsEveryExplicitlySuppliedField() {
        MatchupReportCommand command = MatchupRequestMapper.toCommand(request(
                "PRA", new BigDecimal("36"), "UNDER", "2024-25", "HOME", "LAST_10", new BigDecimal("20.5")));
        assertThat(command.prop()).isEqualTo(PropType.PRA);
        assertThat(command.direction()).isEqualTo(Direction.UNDER);
        assertThat(command.season()).isEqualTo("2024-25");
        assertThat(command.location()).isEqualTo(LocationFilter.HOME);
        assertThat(command.recency()).isEqualTo(RecencyFilter.LAST_10);
        assertThat(command.minMinutes().value()).isEqualByComparingTo(new BigDecimal("20.5"));
    }

    @Test
    void rejectsUnknownOrLowercaseEnumValues() {
        assertCode(ApiErrorCode.UNSUPPORTED_PROP, "prop", () -> MatchupRequestMapper.toCommand(
                request("points", new BigDecimal("24.5"), "OVER", null, null, null, null)));
        assertCode(ApiErrorCode.UNSUPPORTED_PROP, "prop", () -> MatchupRequestMapper.toCommand(
                request("STEALS", new BigDecimal("24.5"), "OVER", null, null, null, null)));
        assertCode(ApiErrorCode.INVALID_DIRECTION, "direction", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "over", null, null, null, null)));
        assertCode(ApiErrorCode.INVALID_LOCATION, "location", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", null, "home", null, null)));
        assertCode(ApiErrorCode.INVALID_RECENCY, "recency", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", null, null, "LAST_2", null)));
    }

    @Test
    void acceptsLineBoundariesAndRejectsJustOutsideAndExcessPrecision() {
        assertThat(MatchupRequestMapper.toCommand(
                                request("POINTS", BigDecimal.ZERO, "OVER", null, null, null, null))
                        .line()
                        .value())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MatchupRequestMapper.toCommand(
                                request("POINTS", new BigDecimal("999"), "OVER", null, null, null, null))
                        .line()
                        .value())
                .isEqualByComparingTo(new BigDecimal("999"));
        assertCode(ApiErrorCode.INVALID_LINE, "line", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("-0.001"), "OVER", null, null, null, null)));
        assertCode(ApiErrorCode.INVALID_LINE, "line", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("999.001"), "OVER", null, null, null, null)));
        assertCode(ApiErrorCode.INVALID_LINE, "line", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5000"), "OVER", null, null, null, null)));
    }

    @Test
    void acceptsMinMinutesBoundariesAndRejectsJustOutsideAndExcessPrecision() {
        assertThat(MatchupRequestMapper.toCommand(
                                request("POINTS", new BigDecimal("24.5"), "OVER", null, null, null,
                                        new BigDecimal("80.000")))
                        .minMinutes()
                        .value())
                .isEqualByComparingTo(new BigDecimal("80"));
        assertCode(ApiErrorCode.INVALID_MIN_MINUTES, "minMinutes", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", null, null, null, new BigDecimal("-0.001"))));
        assertCode(ApiErrorCode.INVALID_MIN_MINUTES, "minMinutes", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", null, null, null, new BigDecimal("80.001"))));
        assertCode(ApiErrorCode.INVALID_MIN_MINUTES, "minMinutes", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", null, null, null, new BigDecimal("20.0000"))));
    }

    @Test
    void rejectsMalformedSeasonBeforeAnyLookup() {
        assertCode(ApiErrorCode.INVALID_SEASON, "season", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", "2024", null, null, null)));
        assertCode(ApiErrorCode.INVALID_SEASON, "season", () -> MatchupRequestMapper.toCommand(
                request("POINTS", new BigDecimal("24.5"), "OVER", "2024-2025", null, null, null)));
    }

    private static void assertCode(ApiErrorCode code, String field, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException exception = (ApiException) thrown;
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.fieldErrors()).singleElement().satisfies(fieldError -> {
                        assertThat(fieldError.field()).isEqualTo(field);
                        assertThat(fieldError.code()).isEqualTo(code);
                    });
                });
    }

    private static MatchupReportRequest request(
            String prop,
            BigDecimal line,
            String direction,
            String season,
            String location,
            String recency,
            BigDecimal minMinutes) {
        return new MatchupReportRequest(
                201_939L, 1_610_612_738L, prop, line, direction, season, location, recency, minMinutes);
    }
}
