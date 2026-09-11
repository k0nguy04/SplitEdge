package dev.splitedge.report.api;

public record PlayerResponse(
        long nbaPlayerId, String firstName, String lastName, String fullName, Long nbaTeamId, boolean active) {}
