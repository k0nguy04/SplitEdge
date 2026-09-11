package dev.splitedge.report;

import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.AWAY_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.AWAY_TEAM;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.HOME_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.HOME_TEAM;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.INCONSISTENT_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.INCONSISTENT_PLAYER;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.NON_FINAL_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.OTHER_OPPONENT_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.OTHER_PLAYER;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.OTHER_PLAYER_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.PRIOR_SEASON_GAME;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.ROSTER_TEAM;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.SUBJECT_PLAYER;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.TIE_BREAK_HIGH;
import static dev.splitedge.report.PlayerGameLineRepositoryFixtures.TIE_BREAK_LOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.splitedge.report.support.GuardedPostgres;
import dev.splitedge.report.support.IntegrationDatabaseSettings;

@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerGameLineRepositoryIT {

    private JdbcClient jdbc;
    private PlayerGameLineRepository repository;

    @BeforeAll
    void setUp() {
        DataSource dataSource =
                GuardedPostgres.connectAndMigrate(IntegrationDatabaseSettings.fromEnvironment());
        jdbc = JdbcClient.create(dataSource);
        repository = new PlayerGameLineRepository(jdbc);
        PlayerGameLineRepositoryFixtures.deleteAll(jdbc);
        PlayerGameLineRepositoryFixtures.insertAll(jdbc);
    }

    @AfterAll
    void tearDown() {
        if (jdbc != null) {
            PlayerGameLineRepositoryFixtures.deleteAll(jdbc);
        }
    }

    @Test
    void homeAppearanceMapsHomeLocationAndAwayOpponent() {
        PlayerGameLine home = line(HOME_GAME);
        assertThat(home.location()).isEqualTo(GameLocation.HOME);
        assertThat(home.nbaTeamId()).isEqualTo(HOME_TEAM);
        assertThat(home.opponentNbaTeamId()).isEqualTo(AWAY_TEAM);
    }

    @Test
    void awayAppearanceMapsAwayLocationAndHomeOpponent() {
        PlayerGameLine away = line(AWAY_GAME);
        assertThat(away.location()).isEqualTo(GameLocation.AWAY);
        assertThat(away.nbaTeamId()).isEqualTo(HOME_TEAM);
        assertThat(away.opponentNbaTeamId()).isEqualTo(AWAY_TEAM);
    }

    @Test
    void gameNightTeamIsIndependentOfCurrentRosterTeam() {
        PlayerGameLine home = line(HOME_GAME);
        assertThat(home.nbaTeamId()).isEqualTo(HOME_TEAM);
        assertThat(home.nbaTeamId()).isNotEqualTo(ROSTER_TEAM);
        long rosterMatches = jdbc.sql("SELECT COUNT(*) FROM players WHERE nba_player_id = :id AND nba_team_id = :roster")
                .param("id", SUBJECT_PLAYER)
                .param("roster", ROSTER_TEAM)
                .query(Long.class)
                .single();
        assertThat(rosterMatches).isEqualTo(1L);
    }

    @Test
    void mapsStoredBoxScoreFieldsExactly() {
        PlayerGameLine home = line(HOME_GAME);
        assertThat(home.nbaPlayerId()).isEqualTo(SUBJECT_PLAYER);
        assertThat(home.nbaGameId()).isEqualTo(HOME_GAME);
        assertThat(home.gameDate()).isEqualTo(LocalDate.of(2024, 10, 22));
        assertThat(home.season()).isEqualTo("2024-25");
        assertThat(home.minutes()).isEqualByComparingTo(new BigDecimal("36.000"));
        assertThat(home.points()).isEqualTo(30);
        assertThat(home.rebounds()).isEqualTo(5);
        assertThat(home.assists()).isEqualTo(8);
        assertThat(home.threePointersMade()).isEqualTo(4);
    }

    @Test
    void returnsMultipleSeasons() {
        assertThat(repository.findByNbaPlayerId(SUBJECT_PLAYER))
                .extracting(PlayerGameLine::season)
                .contains("2023-24", "2024-25");
        assertThat(line(PRIOR_SEASON_GAME).season()).isEqualTo("2023-24");
    }

    @Test
    void ordersByGameDateThenGameIdAscending() {
        assertThat(repository.findByNbaPlayerId(SUBJECT_PLAYER))
                .extracting(PlayerGameLine::nbaGameId)
                .containsExactly(
                        PRIOR_SEASON_GAME,
                        HOME_GAME,
                        AWAY_GAME,
                        OTHER_OPPONENT_GAME,
                        TIE_BREAK_LOW,
                        TIE_BREAK_HIGH);
    }

    @Test
    void excludesOtherPlayersRows() {
        assertThat(repository.findByNbaPlayerId(SUBJECT_PLAYER))
                .extracting(PlayerGameLine::nbaGameId)
                .doesNotContain(OTHER_PLAYER_GAME);
        assertThat(repository.findByNbaPlayerId(OTHER_PLAYER))
                .extracting(PlayerGameLine::nbaGameId)
                .containsExactly(OTHER_PLAYER_GAME);
    }

    @Test
    void schemaForbidsNonFinalGames() {
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO games (
                                    nba_game_id, season, game_date, home_nba_team_id, away_nba_team_id,
                                    home_score, away_score, status)
                                VALUES (:id, '2024-25', DATE '2024-12-20', :home, :away, 1, 1, 'SCHEDULED')
                                """)
                        .param("id", NON_FINAL_GAME)
                        .param("home", HOME_TEAM)
                        .param("away", AWAY_TEAM)
                        .update())
                .hasMessageContaining("games_status_check");
    }

    @Test
    void unknownPlayerReturnsEmptyImmutableList() {
        List<PlayerGameLine> lines = repository.findByNbaPlayerId(9_000_001_999L);
        assertThat(lines).isEmpty();
        assertThatThrownBy(() -> lines.add(line(HOME_GAME))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void statTeamMatchingNeitherSideFailsSafely() {
        assertThatThrownBy(() -> repository.findByNbaPlayerId(INCONSISTENT_PLAYER))
                .isInstanceOf(InconsistentPlayerAppearanceException.class)
                .hasMessageContaining(INCONSISTENT_GAME)
                .hasMessageContaining(String.valueOf(ROSTER_TEAM));
    }

    @Test
    void repositoryRowsFeedMatchupCalculatorWithHandCalculatedResult() {
        List<PlayerGameLine> lines = repository.findByNbaPlayerId(SUBJECT_PLAYER);
        MatchupQuery query = new MatchupQuery(
                AWAY_TEAM,
                PropType.POINTS,
                Line.of(new BigDecimal("24.5")),
                Direction.OVER,
                "2024-25",
                LocationFilter.ALL,
                RecencyFilter.ALL,
                MinMinutes.of(BigDecimal.ZERO));
        MatchupCalculation result = new MatchupCalculator().calculate(lines, query);
        SampleSummary matchup = result.matchup();
        assertThat(matchup.qualifyingGames()).isEqualTo(4);
        assertThat(matchup.hits()).isEqualTo(1);
        assertThat(matchup.misses()).isEqualTo(3);
        assertThat(matchup.pushes()).isZero();
        assertThat(matchup.hitRate()).isEqualTo(ExactFraction.of(1, 4));
        assertThat(matchup.average()).isEqualTo(ExactFraction.of(71, 4));
        assertThat(matchup.median()).isEqualByComparingTo(new BigDecimal("15.5"));
        assertThat(result.baseline().qualifyingGames()).isEqualTo(5);
        assertThat(result.baseline().hits()).isEqualTo(2);
        assertThat(result.baseline().misses()).isEqualTo(3);
        assertThat(result.baseline().hitRate()).isEqualTo(ExactFraction.of(2, 5));
        assertThat(result.hitRateDifferencePoints()).isEqualTo(ExactFraction.of(-15, 1));
        assertThat(result.matchupGames())
                .extracting(classified -> classified.game().nbaGameId())
                .containsExactly(HOME_GAME, AWAY_GAME, TIE_BREAK_LOW, TIE_BREAK_HIGH);
    }

    private PlayerGameLine line(String nbaGameId) {
        return repository.findByNbaPlayerId(SUBJECT_PLAYER).stream()
                .filter(game -> game.nbaGameId().equals(nbaGameId))
                .findFirst()
                .orElseThrow();
    }
}
