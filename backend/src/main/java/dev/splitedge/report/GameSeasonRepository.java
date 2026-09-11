package dev.splitedge.report;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GameSeasonRepository {

    private static final String FIND_DISTINCT_SEASONS = """
            SELECT DISTINCT season
            FROM games
            ORDER BY season ASC
            """;

    private final JdbcClient jdbc;

    public GameSeasonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findStoredSeasons() {
        return List.copyOf(jdbc.sql(FIND_DISTINCT_SEASONS).query(String.class).list());
    }
}
