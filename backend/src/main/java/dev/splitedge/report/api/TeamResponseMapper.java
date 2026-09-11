package dev.splitedge.report.api;

import dev.splitedge.report.TeamProfile;

public final class TeamResponseMapper {

    private TeamResponseMapper() {}

    public static TeamResponse toResponse(TeamProfile profile) {
        return new TeamResponse(
                profile.nbaTeamId(), profile.abbreviation(), profile.city(), profile.nickname(), profile.fullName());
    }
}
