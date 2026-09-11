package dev.splitedge.report;

import static dev.splitedge.report.MatchupReportItFixtures.LATEST_COMPLETED;
import static dev.splitedge.report.MatchupReportItFixtures.OPPONENT;
import static dev.splitedge.report.MatchupReportItFixtures.PLAYER;
import static dev.splitedge.report.MatchupReportItFixtures.TEAM;
import static dev.splitedge.report.MatchupReportItFixtures.VS_OPPONENT_AWAY;
import static dev.splitedge.report.MatchupReportItFixtures.VS_OPPONENT_HOME;
import static dev.splitedge.report.MatchupReportItFixtures.VS_OPPONENT_LATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.splitedge.report.api.MatchupReportResponse;
import dev.splitedge.report.api.MatchupReportResponseMapper;
import dev.splitedge.report.support.GuardedPostgres;
import dev.splitedge.report.support.IntegrationDatabaseSettings;
import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatchupReportServiceIT {

    private JdbcClient jdbc;
    private MatchupReportService service;
    private ImportFreshnessRepository freshness;

    @BeforeAll
    void setUp() {
        DataSource dataSource =
                GuardedPostgres.connectAndMigrate(IntegrationDatabaseSettings.fromEnvironment());
        jdbc = JdbcClient.create(dataSource);
        freshness = new ImportFreshnessRepository(jdbc);
        service = new MatchupReportService(
                new PlayerIdentityRepository(jdbc),
                new TeamIdentityRepository(jdbc),
                new GameSeasonRepository(jdbc),
                new PlayerGameLineRepository(jdbc),
                freshness,
                new MatchupCalculator());
        MatchupReportItFixtures.deleteAll(jdbc);
        MatchupReportItFixtures.insertAll(jdbc);
    }

    @AfterAll
    void tearDown() {
        if (jdbc != null) {
            MatchupReportItFixtures.deleteAll(jdbc);
        }
    }

    @Test
    void producesTheHandCalculatedReportFromStoredRows() {
        MatchupReport report = service.generate(command("2024-25"));
        SampleSummary matchup = report.calculation().matchup();
        assertThat(matchup.qualifyingGames()).isEqualTo(3);
        assertThat(matchup.hits()).isEqualTo(1);
        assertThat(matchup.misses()).isEqualTo(2);
        assertThat(matchup.pushes()).isZero();
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(1, 3));
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(74, 3));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("24"));

        SampleSummary baseline = report.calculation().baseline();
        assertThat(baseline.qualifyingGames()).isEqualTo(4);
        assertThat(baseline.hits()).isEqualTo(2);
        assertThat(baseline.misses()).isEqualTo(2);
        assertThat(baseline.hitRate()).isEqualTo(ExactFraction.of(1, 2));
        assertThat(baseline.average()).isEqualTo(ExactFraction.of(101, 4));
        assertThat(baseline.median()).isEqualByComparingTo(new BigDecimal("25.5"));

        assertThat(report.calculation().hitRateDifferencePoints()).isEqualTo(ExactFraction.of(-50, 3));
        assertThat(report.calculation().sampleQuality()).isEqualTo(SampleQuality.LOW);
        assertThat(report.calculation().matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly(VS_OPPONENT_HOME, VS_OPPONENT_AWAY, VS_OPPONENT_LATE);
        assertThat(report.player().fullName()).isEqualTo("Report Player");
        assertThat(report.opponent().abbreviation()).isEqualTo("ROP");
        assertThat(report.seasonsApplied()).containsExactly("2024-25");
    }

    @Test
    void mapsTheStoredReportIntoPresentationDecimals() {
        MatchupReportResponse response =
                MatchupReportResponseMapper.toResponse(service.generate(command("2024-25")));
        assertThat(response.matchup().hitRate()).isEqualTo(new BigDecimal("0.333"));
        assertThat(response.matchup().average()).isEqualTo(new BigDecimal("24.7"));
        assertThat(response.matchup().median()).isEqualTo(new BigDecimal("24.0"));
        assertThat(response.baseline().hitRate()).isEqualTo(new BigDecimal("0.500"));
        assertThat(response.baseline().average()).isEqualTo(new BigDecimal("25.3"));
        assertThat(response.baseline().median()).isEqualTo(new BigDecimal("25.5"));
        assertThat(response.comparison().hitRateDifferencePoints()).isEqualTo(new BigDecimal("-16.7"));
        assertThat(response.dataFreshness().completedAt()).isEqualTo(LATEST_COMPLETED);
        assertThat(response.opponentContext().available()).isFalse();
        assertThat(response.warnings()).isEmpty();

        MatchupReportResponse.Game first = response.games().getFirst();
        assertThat(first.nbaGameId()).isEqualTo(VS_OPPONENT_HOME);
        assertThat(first.gameDate()).isEqualTo(LocalDate.of(2024, 10, 22));
        assertThat(first.location()).isEqualTo("HOME");
        assertThat(first.nbaTeamId()).isEqualTo(TEAM);
        assertThat(first.opponentNbaTeamId()).isEqualTo(OPPONENT);
        assertThat(response.games().get(1).location()).isEqualTo("AWAY");
    }

    @Test
    void omittedSeasonAppliesEverySortedStoredSeason() {
        assertThat(service.generate(command(null)).seasonsApplied()).contains("2023-24", "2024-25");
    }

    @Test
    void unknownIdentitiesAndAbsentSeasonsAreRejected() {
        assertThatThrownBy(() -> service.generate(new MatchupReportCommand(
                        9_000_002_999L,
                        OPPONENT,
                        PropType.POINTS,
                        Line.of(new BigDecimal("24.5")),
                        Direction.OVER,
                        "2024-25",
                        LocationFilter.ALL,
                        RecencyFilter.ALL,
                        MinMinutes.of(BigDecimal.ZERO))))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.UNKNOWN_PLAYER));

        assertThatThrownBy(() -> service.generate(new MatchupReportCommand(
                        PLAYER,
                        9_000_000_999L,
                        PropType.POINTS,
                        Line.of(new BigDecimal("24.5")),
                        Direction.OVER,
                        "2024-25",
                        LocationFilter.ALL,
                        RecencyFilter.ALL,
                        MinMinutes.of(BigDecimal.ZERO))))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.UNKNOWN_OPPONENT));

        assertThatThrownBy(() -> service.generate(command("2019-20")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).code())
                        .isEqualTo(ApiErrorCode.INVALID_SEASON));
    }

    @Test
    void latestCompletedGamesStatsRunWinsOverOtherStatusesAndTypes() {
        assertThat(freshness.findLatestCompletedGamesStats()).contains(LATEST_COMPLETED);
    }

    @Test
    void noSuccessfulGamesStatsImportReturnsEmptyFreshness() {
        jdbc.sql("DELETE FROM import_runs").update();
        MatchupReportItFixtures.insertImportRun(jdbc, "GAMES_STATS", "RUNNING", null);
        MatchupReportItFixtures.insertImportRun(jdbc, "GAMES_STATS", "FAILED", "2025-04-01T00:00:00Z");
        MatchupReportItFixtures.insertImportRun(jdbc, "TEAMS_PLAYERS", "COMPLETED", "2025-05-01T00:00:00Z");
        try {
            assertThat(freshness.findLatestCompletedGamesStats()).isEmpty();
            assertThat(MatchupReportResponseMapper.toResponse(service.generate(command("2024-25")))
                            .warnings())
                    .containsExactly("NO_SUCCESSFUL_GAMES_STATS_IMPORT");
        } finally {
            jdbc.sql("DELETE FROM import_runs").update();
            MatchupReportItFixtures.insertImportRun(
                    jdbc, "GAMES_STATS", "COMPLETED", LATEST_COMPLETED.toString());
        }
    }

    private static MatchupReportCommand command(String season) {
        return new MatchupReportCommand(
                PLAYER,
                OPPONENT,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                season,
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
    }
}
