# Architecture decisions

## ADR-001: Modular monolith

**Decision:** Use one Spring Boot application organized by domain.

**Reason:** The MVP does not need independently deployed services. A modular monolith keeps local development and free hosting simple while preserving clear boundaries.

## ADR-002: Separate Python importer

**Decision:** Use Python for batch ingestion and normalization, but not as an always-running service.

**Reason:** Python has strong data tooling, while Spring Boot remains the primary portfolio backend. A scheduled batch process costs less and fails independently of report serving.

## ADR-003: PostgreSQL as normalized source of truth

**Decision:** Reports use locally stored normalized data.

**Reason:** User requests must not depend on the availability or latency of an external NBA data source.

## ADR-004: No paid feeds in the MVP

**Decision:** Users enter prop lines manually and the application does not ingest live odds.

**Reason:** This preserves the $0/month target and keeps the project focused on transparent matchup research.

## ADR-005: No Matchup Score at initial launch

**Decision:** Implement factual statistics before any composite score.

**Reason:** A score must not launch until its inputs, weights, limitations, and tests are complete.
