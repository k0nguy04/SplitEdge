package dev.splitedge.importstatus;

/** Bounded aggregate counts across every foundation table. */
public record DataStatusCounts(long games, long playerGameStats, long players, long activePlayers, long teams) {}
