package dev.splitedge.report;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerIdentityRepository {

    private static final String FIND_BY_ID = """
            SELECT nba_player_id, full_name
            FROM players
            WHERE nba_player_id = :nbaPlayerId
            """;

    private static final String FIND_PROFILE_BY_ID = """
            SELECT nba_player_id, first_name, last_name, full_name, is_active, nba_team_id
            FROM players
            WHERE nba_player_id = :nbaPlayerId
            """;

    private final JdbcClient jdbc;

    public PlayerIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PlayerIdentity> findByNbaPlayerId(long nbaPlayerId) {
        if (nbaPlayerId <= 0) {
            return Optional.empty();
        }
        return jdbc.sql(FIND_BY_ID)
                .param("nbaPlayerId", nbaPlayerId)
                .query((rs, rowNum) -> new PlayerIdentity(rs.getLong("nba_player_id"), rs.getString("full_name")))
                .optional();
    }

    /**
     * Full stored identity for one player, including inactive/historical players.
     * Never fetches game history and never calls an external NBA source.
     */
    public Optional<PlayerProfile> findProfileByNbaPlayerId(long nbaPlayerId) {
        if (nbaPlayerId <= 0) {
            return Optional.empty();
        }
        return jdbc.sql(FIND_PROFILE_BY_ID)
                .param("nbaPlayerId", nbaPlayerId)
                .query((rs, rowNum) -> new PlayerProfile(
                        rs.getLong("nba_player_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("full_name"),
                        rs.getObject("nba_team_id", Long.class),
                        rs.getBoolean("is_active")))
                .optional();
    }
}
