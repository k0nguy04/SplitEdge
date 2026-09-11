package dev.splitedge.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.splitedge.report.GameResult;
import dev.splitedge.report.SampleQuality;

public record MatchupReportResponse(
        Criteria criteria,
        Sample matchup,
        Sample baseline,
        Comparison comparison,
        SampleQuality sampleQuality,
        DataFreshness dataFreshness,
        OpponentContext opponentContext,
        List<Game> games,
        List<ChartPoint> chart,
        List<String> warnings) {

    public record Player(long nbaPlayerId, String fullName) {}

    public record Opponent(long nbaTeamId, String abbreviation, String fullName) {}

    public record Criteria(
            Player player,
            Opponent opponent,
            String prop,
            BigDecimal line,
            String direction,
            List<String> seasonsApplied,
            String location,
            String recency,
            BigDecimal minMinutes) {}

    public record Sample(
            int qualifyingGames,
            int hits,
            int misses,
            int pushes,
            BigDecimal hitRate,
            BigDecimal average,
            BigDecimal median) {}

    public record Comparison(BigDecimal hitRateDifferencePoints) {}

    public record DataFreshness(String importType, Instant completedAt) {}

    public record OpponentContext(boolean available, String reason) {}

    public record Game(
            String nbaGameId,
            LocalDate gameDate,
            String season,
            String location,
            long nbaTeamId,
            long opponentNbaTeamId,
            BigDecimal minutes,
            BigDecimal propValue,
            BigDecimal line,
            GameResult result) {}

    public record ChartPoint(
            String nbaGameId, LocalDate gameDate, BigDecimal propValue, GameResult result) {}
}
