package dev.splitedge.report;

import static dev.splitedge.report.MatchupFixtures.BOS;
import static dev.splitedge.report.MatchupFixtures.eightGameSeason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

class MatchupReportServiceTest {

    private static final long PLAYER_ID = 201_939L;
    private static final Instant COMPLETED_AT = Instant.parse("2025-03-02T08:15:30Z");

    private final PlayerIdentityRepository players = mock(PlayerIdentityRepository.class);
    private final TeamIdentityRepository teams = mock(TeamIdentityRepository.class);
    private final GameSeasonRepository seasons = mock(GameSeasonRepository.class);
    private final PlayerGameLineRepository history = mock(PlayerGameLineRepository.class);
    private final ImportFreshnessRepository freshness = mock(ImportFreshnessRepository.class);
    private final MatchupCalculator calculator = spy(new MatchupCalculator());

    private final MatchupReportService service =
            new MatchupReportService(players, teams, seasons, history, freshness, calculator);

    @BeforeEach
    void stubHappyPath() {
        when(players.findByNbaPlayerId(PLAYER_ID))
                .thenReturn(Optional.of(new PlayerIdentity(PLAYER_ID, "Test Player")));
        when(teams.findByNbaTeamId(BOS))
                .thenReturn(Optional.of(new TeamIdentity(BOS, "BOS", "Boston Celtics")));
        when(seasons.findStoredSeasons()).thenReturn(List.of("2023-24", "2024-25", "2025-26"));
        when(history.findByNbaPlayerId(PLAYER_ID)).thenReturn(eightGameSeason());
        when(freshness.findLatestCompletedGamesStats()).thenReturn(Optional.of(COMPLETED_AT));
    }

    @Test
    void loadsPlayerHistoryAndRunsTheCalculatorExactlyOnce() {
        MatchupReport report = service.generate(command("2024-25"));
        verify(history, times(1)).findByNbaPlayerId(PLAYER_ID);
        verify(calculator, times(1)).calculate(eightGameSeason(), command("2024-25").toQuery());
        assertThat(report.player().fullName()).isEqualTo("Test Player");
        assertThat(report.opponent().abbreviation()).isEqualTo("BOS");
        assertThat(report.calculation().matchup().qualifyingGames()).isEqualTo(4);
        assertThat(report.calculation().matchup().hitRate()).isEqualTo(ExactFraction.of(1, 4));
        assertThat(report.gamesStatsCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void explicitSeasonProducesASingleAppliedSeason() {
        assertThat(service.generate(command("2024-25")).seasonsApplied()).containsExactly("2024-25");
    }

    @Test
    void omittedSeasonAppliesEverySortedStoredSeason() {
        assertThat(service.generate(command(null)).seasonsApplied())
                .containsExactly("2023-24", "2024-25", "2025-26");
    }

    @Test
    void seasonAbsentFromStoredGamesIsRejectedWithoutCalculating() {
        assertThatThrownBy(() -> service.generate(command("2019-20")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.INVALID_SEASON));
        verifyNoInteractions(history);
        verify(calculator, never()).calculate(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownPlayerIsRejectedBeforeLoadingHistory() {
        when(players.findByNbaPlayerId(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(command("2024-25")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.UNKNOWN_PLAYER));
        verifyNoInteractions(history);
    }

    @Test
    void unknownOpponentIsRejectedBeforeLoadingHistory() {
        when(teams.findByNbaTeamId(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(command("2024-25")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.UNKNOWN_OPPONENT));
        verifyNoInteractions(history);
    }

    @Test
    void knownPlayerWithNoQualifyingRowsStillProducesAReport() {
        when(history.findByNbaPlayerId(PLAYER_ID)).thenReturn(List.of());
        MatchupReport report = service.generate(command("2024-25"));
        assertThat(report.calculation().matchup().qualifyingGames()).isZero();
        assertThat(report.calculation().matchup().hitRate()).isNull();
        assertThat(report.calculation().sampleQuality()).isNull();
    }

    @Test
    void missingSuccessfulImportLeavesFreshnessNull() {
        when(freshness.findLatestCompletedGamesStats()).thenReturn(Optional.empty());
        assertThat(service.generate(command("2024-25")).gamesStatsCompletedAt()).isNull();
    }

    @Test
    void nonPositiveIdentifiersAreRejectedBeforeAnyLookup() {
        MatchupReportCommand invalidPlayer = new MatchupReportCommand(
                0,
                BOS,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                null,
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
        assertThatThrownBy(() -> service.generate(invalidPlayer))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        verifyNoInteractions(players, teams, history);
    }

    private static MatchupReportCommand command(String season) {
        return new MatchupReportCommand(
                PLAYER_ID,
                BOS,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                season,
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
    }
}
