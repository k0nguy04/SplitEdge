package dev.splitedge.report;

import java.util.List;
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

    private static final String FIND_ALL_ORDERED_BY_ABBREVIATION = """
            SELECT nba_team_id, abbreviation, city, nickname, full_name
            FROM teams
            ORDER BY abbreviation ASC, nba_team_id ASC
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

    /** Every stored team, ordered deterministically by abbreviation then NBA team ID. */
    public List<TeamProfile> findAllOrderedByAbbreviation() {
        return List.copyOf(jdbc.sql(FIND_ALL_ORDERED_BY_ABBREVIATION)
                .query((rs, rowNum) -> new TeamProfile(
                        rs.getLong("nba_team_id"),
                        rs.getString("abbreviation"),
                        rs.getString("city"),
                        rs.getString("nickname"),
                        rs.getString("full_name")))
                .list());
    }
}
