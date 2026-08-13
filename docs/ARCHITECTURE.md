# Architecture

## System context

SplitEdge uses a modular monolith for the application API and a separate batch importer.

1. A scheduled Python process retrieves and normalizes NBA data.
2. The importer writes idempotent records to PostgreSQL.
3. Spring Boot queries stored data and calculates matchup reports.
4. React consumes the REST API and presents transparent supporting evidence.
5. GitHub Actions will eventually run tests and the daily importer.

No external sports API is called while serving a user report.

## Backend modules

The backend will evolve around domain modules rather than technical-layer folders:

- `player`
- `team`
- `game`
- `report`
- `importstatus`
- `shared`

Each domain owns its API, application logic, persistence types, and tests. Cross-domain dependencies must point toward stable application interfaces.

## Data ownership

PostgreSQL is the source of truth for normalized players, teams, games, player game statistics, team game statistics, and import runs. Derived combination props are calculated at query time rather than stored.

## Caching

The MVP uses an in-process cache. Hosted Redis is intentionally deferred because the free portfolio deployment will run one backend instance.

## Deployment target

- Static frontend hosting
- Free sleeping Java web service
- Free managed PostgreSQL
- Scheduled public-repository workflow for imports

Every hosted dependency must have a local fallback.
