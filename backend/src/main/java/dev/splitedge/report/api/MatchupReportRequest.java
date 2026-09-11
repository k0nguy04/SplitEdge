package dev.splitedge.report.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import tools.jackson.databind.annotation.JsonDeserialize;

public record MatchupReportRequest(
        @NotNull(message = "nbaPlayerId is required")
        @Positive(message = "nbaPlayerId must be a positive NBA player ID")
        Long nbaPlayerId,
        @NotNull(message = "nbaOpponentTeamId is required")
        @Positive(message = "nbaOpponentTeamId must be a positive NBA team ID")
        Long nbaOpponentTeamId,
        @NotNull(message = "prop is required") String prop,
        @NotNull(message = "line is required")
        @JsonDeserialize(using = StrictDecimalDeserializer.class)
        BigDecimal line,
        @NotNull(message = "direction is required") String direction,
        String season,
        String location,
        String recency,
        @JsonDeserialize(using = StrictDecimalDeserializer.class) BigDecimal minMinutes) {}
