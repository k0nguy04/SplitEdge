package dev.splitedge.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerGameLineRepository {

    private static final String FIND_BY_PLAYER = """
            SELECT s.nba_player_id,
                   s.nba_game_id,
                   g.game_date,
                   g.season,
                   s.nba_team_id,
                   g.home_nba_team_id,
                   g.away_nba_team_id,
                   s.minutes,
                   s.points,
                   s.rebounds,
                   s.assists,
                   s.three_pointers_made
            FROM player_game_stats s
            INNER JOIN games g ON g.nba_game_id = s.nba_game_id
            WHERE s.nba_player_id = :nbaPlayerId
              AND g.status = 'FINAL'
            ORDER BY g.game_date ASC, g.nba_game_id ASC
            """;

    private final JdbcClient jdbc;

    public PlayerGameLineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlayerGameLine> findByNbaPlayerId(long nbaPlayerId) {
        if (nbaPlayerId <= 0) {
            throw new IllegalArgumentException("nbaPlayerId must be positive");
        }
        return List.copyOf(jdbc.sql(FIND_BY_PLAYER)
                .param("nbaPlayerId", nbaPlayerId)
                .query(PlayerGameLineRepository::mapRow)
                .list());
    }

    static PlayerGameLine mapRow(ResultSet rs, int rowNum) throws SQLException {
        long nbaTeamId = rs.getLong("nba_team_id");
        long homeNbaTeamId = rs.getLong("home_nba_team_id");
        long awayNbaTeamId = rs.getLong("away_nba_team_id");
        String nbaGameId = rs.getString("nba_game_id");
        GameLocation location;
        long opponentNbaTeamId;
        if (nbaTeamId == homeNbaTeamId) {
            location = GameLocation.HOME;
            opponentNbaTeamId = awayNbaTeamId;
        } else if (nbaTeamId == awayNbaTeamId) {
            location = GameLocation.AWAY;
            opponentNbaTeamId = homeNbaTeamId;
        } else {
            throw new InconsistentPlayerAppearanceException(
                    nbaGameId, nbaTeamId, homeNbaTeamId, awayNbaTeamId);
        }
        return new PlayerGameLine(
                nbaGameId,
                rs.getObject("game_date", LocalDate.class),
                rs.getString("season"),
                location,
                rs.getLong("nba_player_id"),
                nbaTeamId,
                opponentNbaTeamId,
                rs.getBigDecimal("minutes"),
                rs.getInt("points"),
                rs.getInt("rebounds"),
                rs.getInt("assists"),
                rs.getInt("three_pointers_made"));
    }
}
