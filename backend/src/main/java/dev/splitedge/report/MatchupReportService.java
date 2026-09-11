package dev.splitedge.report;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

@Service
public class MatchupReportService {

    private final PlayerIdentityRepository playerIdentities;
    private final TeamIdentityRepository teamIdentities;
    private final GameSeasonRepository gameSeasons;
    private final PlayerGameLineRepository playerGameLines;
    private final ImportFreshnessRepository importFreshness;
    private final MatchupCalculator calculator;

    public MatchupReportService(
            PlayerIdentityRepository playerIdentities,
            TeamIdentityRepository teamIdentities,
            GameSeasonRepository gameSeasons,
            PlayerGameLineRepository playerGameLines,
            ImportFreshnessRepository importFreshness,
            MatchupCalculator calculator) {
        this.playerIdentities = Objects.requireNonNull(playerIdentities);
        this.teamIdentities = Objects.requireNonNull(teamIdentities);
        this.gameSeasons = Objects.requireNonNull(gameSeasons);
        this.playerGameLines = Objects.requireNonNull(playerGameLines);
        this.importFreshness = Objects.requireNonNull(importFreshness);
        this.calculator = Objects.requireNonNull(calculator);
    }

    public MatchupReport generate(MatchupReportCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositiveId(command.nbaPlayerId(), "nbaPlayerId");
        requirePositiveId(command.nbaOpponentTeamId(), "nbaOpponentTeamId");

        PlayerIdentity player = playerIdentities
                .findByNbaPlayerId(command.nbaPlayerId())
                .orElseThrow(() -> ApiException.field(
                        ApiErrorCode.UNKNOWN_PLAYER, "nbaPlayerId", "no stored player has that NBA player ID"));
        TeamIdentity opponent = teamIdentities
                .findByNbaTeamId(command.nbaOpponentTeamId())
                .orElseThrow(() -> ApiException.field(
                        ApiErrorCode.UNKNOWN_OPPONENT,
                        "nbaOpponentTeamId",
                        "no stored team has that NBA team ID"));

        List<String> storedSeasons = gameSeasons.findStoredSeasons();
        List<String> seasonsApplied = resolveSeasons(command.season(), storedSeasons);

        List<PlayerGameLine> history = playerGameLines.findByNbaPlayerId(command.nbaPlayerId());
        MatchupCalculation calculation = calculator.calculate(history, command.toQuery());

        return new MatchupReport(
                player,
                opponent,
                command,
                seasonsApplied,
                calculation,
                importFreshness.findLatestCompletedGamesStats().orElse(null));
    }

    private static List<String> resolveSeasons(String season, List<String> storedSeasons) {
        if (season == null) {
            return storedSeasons;
        }
        if (!storedSeasons.contains(season)) {
            throw ApiException.field(
                    ApiErrorCode.INVALID_SEASON, "season", "season is not present in stored games");
        }
        return List.of(season);
    }

    private static void requirePositiveId(long value, String field) {
        if (value <= 0) {
            throw ApiException.field(
                    ApiErrorCode.VALIDATION_FAILED, field, field + " must be a positive NBA ID");
        }
    }
}
