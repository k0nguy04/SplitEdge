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
}
