package dev.splitedge.importstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.splitedge.report.GameSeasonRepository;
import dev.splitedge.report.ImportFreshnessRepository;
import dev.splitedge.report.ImportRunSummary;

class DataStatusServiceTest {

    private ImportFreshnessRepository importFreshness;
    private GameSeasonRepository gameSeasons;
    private DataStatusRepository dataStatusRepository;
    private DataStatusService service;

    @BeforeEach
    void setUp() {
        importFreshness = mock(ImportFreshnessRepository.class);
        gameSeasons = mock(GameSeasonRepository.class);
        dataStatusRepository = mock(DataStatusRepository.class);
        service = new DataStatusService(importFreshness, gameSeasons, dataStatusRepository);
    }

    @Test
    void readyWhenCompletedRunExistsAndGamesAndStatsArePresent() {
        ImportRunSummary run = new ImportRunSummary(4, Instant.parse("2025-03-02T08:15:30Z"), 82747, 0);
        given(importFreshness.findLatestCompletedGamesStatsRun()).willReturn(Optional.of(run));
        given(gameSeasons.findStoredSeasons()).willReturn(List.of("2023-24", "2024-25"));
        DataStatusCounts counts = new DataStatusCounts(3690, 79057, 801, 525, 30);
        given(dataStatusRepository.countAll()).willReturn(counts);

        DataStatus status = service.currentStatus();

        assertThat(status.dataImported()).isTrue();
        assertThat(status.status()).isEqualTo(DataStatusLevel.READY);
        assertThat(status.latestGamesStatsImport()).isEqualTo(run);
        assertThat(status.seasons()).containsExactly("2023-24", "2024-25");
        assertThat(status.counts()).isEqualTo(counts);
    }

    @Test
    void emptyWhenNoCompletedGamesStatsImportExistsEvenIfRowsAreStored() {
        given(importFreshness.findLatestCompletedGamesStatsRun()).willReturn(Optional.empty());
        given(gameSeasons.findStoredSeasons()).willReturn(List.of("2024-25"));
        DataStatusCounts counts = new DataStatusCounts(10, 20, 5, 5, 2);
        given(dataStatusRepository.countAll()).willReturn(counts);

        DataStatus status = service.currentStatus();

        assertThat(status.dataImported()).isFalse();
        assertThat(status.status()).isEqualTo(DataStatusLevel.EMPTY);
        assertThat(status.latestGamesStatsImport()).isNull();
        assertThat(status.counts()).isEqualTo(counts);
    }

    @Test
    void emptyWhenCompletedRunExistsButNoGamesAreStoredYet() {
        ImportRunSummary run = new ImportRunSummary(1, Instant.parse("2025-01-01T00:00:00Z"), 0, 0);
        given(importFreshness.findLatestCompletedGamesStatsRun()).willReturn(Optional.of(run));
        given(gameSeasons.findStoredSeasons()).willReturn(List.of());
        DataStatusCounts counts = new DataStatusCounts(0, 0, 0, 0, 0);
        given(dataStatusRepository.countAll()).willReturn(counts);

        DataStatus status = service.currentStatus();

        assertThat(status.dataImported()).isFalse();
        assertThat(status.status()).isEqualTo(DataStatusLevel.EMPTY);
        // The completed run still exists, so it must still be reported even though status is EMPTY.
        assertThat(status.latestGamesStatsImport()).isEqualTo(run);
    }

    @Test
    void emptyWhenCompletedRunAndGamesExistButNoPlayerGameStatsYet() {
        ImportRunSummary run = new ImportRunSummary(1, Instant.parse("2025-01-01T00:00:00Z"), 0, 0);
        given(importFreshness.findLatestCompletedGamesStatsRun()).willReturn(Optional.of(run));
        given(gameSeasons.findStoredSeasons()).willReturn(List.of("2024-25"));
        DataStatusCounts counts = new DataStatusCounts(5, 0, 10, 8, 2);
        given(dataStatusRepository.countAll()).willReturn(counts);

        DataStatus status = service.currentStatus();

        assertThat(status.dataImported()).isFalse();
        assertThat(status.status()).isEqualTo(DataStatusLevel.EMPTY);
        assertThat(status.latestGamesStatsImport()).isEqualTo(run);
    }

    @Test
    void seasonsAndCountsAlwaysReflectRepositoryValuesRegardlessOfReadiness() {
        given(importFreshness.findLatestCompletedGamesStatsRun()).willReturn(Optional.empty());
        given(gameSeasons.findStoredSeasons()).willReturn(List.of("2021-22", "2022-23"));
        DataStatusCounts counts = new DataStatusCounts(7, 9, 11, 3, 4);
        given(dataStatusRepository.countAll()).willReturn(counts);

        DataStatus status = service.currentStatus();

        assertThat(status.seasons()).containsExactly("2021-22", "2022-23");
        assertThat(status.counts()).isEqualTo(counts);
    }
}
