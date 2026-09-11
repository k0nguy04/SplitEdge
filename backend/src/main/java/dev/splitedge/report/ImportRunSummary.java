package dev.splitedge.report;

import java.time.Instant;

/** Summary of one completed import run, used to report data freshness. */
public record ImportRunSummary(long runId, Instant completedAt, int recordsProcessed, int recordsFailed) {}
