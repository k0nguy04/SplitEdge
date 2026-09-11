package dev.splitedge.report;

/**
 * Full stored identity for one player, using only canonical NBA IDs. {@code nbaTeamId}
 * is {@code null} when the player has no current roster team on record.
 */
public record PlayerProfile(
        long nbaPlayerId, String firstName, String lastName, String fullName, Long nbaTeamId, boolean active) {}
