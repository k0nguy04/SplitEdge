package dev.splitedge.report.api;

import dev.splitedge.report.PlayerProfile;

public final class PlayerResponseMapper {

    private PlayerResponseMapper() {}

    public static PlayerResponse toResponse(PlayerProfile profile) {
        return new PlayerResponse(
                profile.nbaPlayerId(),
                profile.firstName(),
                profile.lastName(),
                profile.fullName(),
                profile.nbaTeamId(),
                profile.active());
    }
}
