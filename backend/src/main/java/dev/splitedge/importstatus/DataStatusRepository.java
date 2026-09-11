package dev.splitedge.importstatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bounded aggregate counts only. Never loads table rows into memory: every count is a
 * single {@code COUNT(*)} evaluated by PostgreSQL.
 */
@Repository
public class DataStatusRepository {

    private static final String COUNTS_QUERY = """
            SELECT
                (SELECT COUNT(*) FROM games) AS games,
                (SELECT COUNT(*) FROM player_game_stats) AS player_game_stats,
                (SELECT COUNT(*) FROM players) AS players,
                (SELECT COUNT(*) FROM players WHERE is_active = TRUE) AS active_players,
                (SELECT COUNT(*) FROM teams) AS teams
            """;

    private final JdbcClient jdbc;

    public DataStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DataStatusCounts countAll() {
        return jdbc.sql(COUNTS_QUERY)
                .query((rs, rowNum) -> new DataStatusCounts(
                        rs.getLong("games"),
                        rs.getLong("player_game_stats"),
                        rs.getLong("players"),
                        rs.getLong("active_players"),
                        rs.getLong("teams")))
                .single();
    }
}
