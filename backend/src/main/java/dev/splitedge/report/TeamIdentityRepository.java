package dev.splitedge.report;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TeamIdentityRepository {

    private static final String FIND_BY_ID = """
            SELECT nba_team_id, abbreviation, full_name
            FROM teams
            WHERE nba_team_id = :nbaTeamId
            """;

    private final JdbcClient jdbc;

    public TeamIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TeamIdentity> findByNbaTeamId(long nbaTeamId) {
        if (nbaTeamId <= 0) {
            return Optional.empty();
        }
        return jdbc.sql(FIND_BY_ID)
                .param("nbaTeamId", nbaTeamId)
                .query((rs, rowNum) -> new TeamIdentity(
                        rs.getLong("nba_team_id"), rs.getString("abbreviation"), rs.getString("full_name")))
                .optional();
    }
}
