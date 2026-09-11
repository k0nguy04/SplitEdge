package dev.splitedge.report;

/**
 * Full stored identity for one team, using only canonical NBA IDs. Carries no
 * defensive statistics.
 */
public record TeamProfile(long nbaTeamId, String abbreviation, String city, String nickname, String fullName) {}
