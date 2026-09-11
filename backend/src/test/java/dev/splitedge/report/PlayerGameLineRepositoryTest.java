package dev.splitedge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.AbstractDataSource;

class PlayerGameLineRepositoryTest {

    @Test
    void rejectsNonPositivePlayerIdWithoutOpeningAConnection() {
        AtomicBoolean connected = new AtomicBoolean(false);
        DataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                connected.set(true);
                throw new IllegalStateException("SQL must not run");
            }

            @Override
            public Connection getConnection(String username, String password) {
                return getConnection();
            }
        };
        PlayerGameLineRepository repository = new PlayerGameLineRepository(JdbcClient.create(dataSource));
        assertThatThrownBy(() -> repository.findByNbaPlayerId(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> repository.findByNbaPlayerId(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(connected).isFalse();
    }

    @Test
    void mapRowFailsWhenStatTeamMatchesNeitherHomeNorAway() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("nba_team_id")).thenReturn(9_000_000_003L);
        when(resultSet.getLong("home_nba_team_id")).thenReturn(9_000_000_001L);
        when(resultSet.getLong("away_nba_team_id")).thenReturn(9_000_000_002L);
        when(resultSet.getString("nba_game_id")).thenReturn("0029900088");
        assertThatThrownBy(() -> PlayerGameLineRepository.mapRow(resultSet, 0))
                .isInstanceOf(InconsistentPlayerAppearanceException.class)
                .hasMessageContaining("0029900088")
                .hasMessageContaining("9000000003");
    }

    @Test
    void mapRowDerivesHomeLocationAndAwayOpponent() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("nba_team_id")).thenReturn(9_000_000_001L);
        when(resultSet.getLong("home_nba_team_id")).thenReturn(9_000_000_001L);
        when(resultSet.getLong("away_nba_team_id")).thenReturn(9_000_000_002L);
        when(resultSet.getString("nba_game_id")).thenReturn("0029900001");
        when(resultSet.getObject("game_date", LocalDate.class)).thenReturn(LocalDate.of(2024, 10, 22));
        when(resultSet.getString("season")).thenReturn("2024-25");
        when(resultSet.getLong("nba_player_id")).thenReturn(9_000_001_001L);
        when(resultSet.getBigDecimal("minutes")).thenReturn(new BigDecimal("36.000"));
        when(resultSet.getInt("points")).thenReturn(30);
        when(resultSet.getInt("rebounds")).thenReturn(5);
        when(resultSet.getInt("assists")).thenReturn(8);
        when(resultSet.getInt("three_pointers_made")).thenReturn(4);
        PlayerGameLine line = PlayerGameLineRepository.mapRow(resultSet, 0);
        assertThat(line.location()).isEqualTo(GameLocation.HOME);
        assertThat(line.opponentNbaTeamId()).isEqualTo(9_000_000_002L);
        assertThat(line.nbaTeamId()).isEqualTo(9_000_000_001L);
        assertThat(line.points()).isEqualTo(30);
    }
}
