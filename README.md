# SplitEdge

> Understand the matchup behind the prop.

SplitEdge is an NBA player-prop research platform that turns historical game data into transparent, opponent-specific matchup reports. The MVP will let a user select a player, opponent, prop, line, and direction, then compare the matchup result with the player's overall baseline and inspect every qualifying game.

## Repository status

- **Milestone 1 (data proof of concept): complete.** The Python importer loads
  NBA teams, active players, completed regular-season games, and MVP box-score
  stats into PostgreSQL, with fixture-based tests and no duplicate imports.
- **Milestone 2 (analytics engine — calculation and API layer): complete once
  CI passes.** The backend serves player/team identity, the static prop
  catalog, import/data status, and the matchup report calculation (individual
  and combination props, baseline comparison, sample-quality label) entirely
  from stored PostgreSQL data. See [API (Milestone 2)](#api-milestone-2) below.
- **Milestone 3 (matchup interface / frontend): not started.** This is the
  next milestone.
- **Not yet implemented (later milestones):** team defensive statistics and
  opponent context (Milestone 4), caching, scheduling, and automated imports
  (Milestone 5), and live odds/sportsbook integration (out of MVP scope
  entirely — see [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md)).

## Architecture

| Directory | Technology | Responsibility |
|---|---|---|
| `backend/` | Java 21, Spring Boot | REST API and matchup calculations |
| `frontend/` | React, TypeScript, Vite | Research interface and reports |
| `importer/` | Python | NBA data ingestion and normalization |
| `docs/` | Markdown | Architecture, decisions, and product specification |
| `infra/` | Docker Compose | Local PostgreSQL development environment |

## Local prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+
- Python 3.12+
- PostgreSQL 16+ or Docker

## Configuration and credentials

No password is committed anywhere in this repository. Every local password is
supplied by you, through environment variables or your own `.env` file (copied
from `.env.example`), and is never checked into source control.

There are three independent, non-interchangeable credential paths:

| Purpose | Variables | Where it's read |
|---|---|---|
| Backend application startup (`mvn spring-boot:run`, the packaged jar) | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and either `SPRING_DATASOURCE_PASSWORD` or the `POSTGRES_PASSWORD` alias | `backend/src/main/resources/application.yml` |
| An explicitly invoked Flyway Maven goal (`mvn flyway:migrate`) | `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` (or the equivalent `-Dflyway.url=`/`-Dflyway.user=`/`-Dflyway.password=` command-line flags) | `backend/pom.xml`; has no default and never targets the primary database by default |
| The guarded PostgreSQL integration-test suite (`mvn -Ppostgres-it verify`, from `backend/`) | `SPRING_DATASOURCE_URL` + `SPRING_DATASOURCE_USERNAME`/`POSTGRES_USER` + `SPRING_DATASOURCE_PASSWORD`/`POSTGRES_PASSWORD`, or `DATABASE_URL` | `IntegrationDatabaseSettings`; refuses to run against anything but a database literally named `splitedge_backend_test` (`IntegrationDatabaseGuard`) |

Backend application startup and the guarded test suite intentionally accept the
same variable names — that's what lets one local PostgreSQL instance serve
both, provided the database used for tests is named `splitedge_backend_test`,
never the primary `splitedge` database. The Flyway Maven goal is unrelated to
both: it is a manual, explicitly-invoked convenience command with its own
dedicated variables, and does not run automatically during `mvn verify`, `mvn
-Ppostgres-it verify`, or normal application startup.

## Quick start

1. Copy `.env.example` to `.env`.
2. Start PostgreSQL with `docker compose -f infra/compose.yaml up -d` if Docker is available.
3. Start the backend from `backend/` with `mvn spring-boot:run`.
4. Start the frontend from `frontend/` with `npm install` and `npm run dev`.
5. Apply database migrations from `backend/` with `mvn flyway:migrate`, after setting `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD` (see [Configuration and credentials](#configuration-and-credentials)).
6. Run importer unit tests from `importer/` with `python -m pip install -e ".[dev]"` and `python -m pytest -m "not integration"`.
7. Run a live teams-and-players import with `python -m splitedge_importer` after setting `DATABASE_URL` and `NBA_SEASON`. Automated tests never call NBA endpoints; they use fixture data.

Schema changes are applied only through Flyway. Do not run the SQL migration files directly.

The application is designed to support a $0/month portfolio deployment. No paid sports feed, odds provider, or AI API is required.

## Importer commands

Default command (teams and active players):

```powershell
python -m splitedge_importer
python -m splitedge_importer teams-players
```

Games and box scores (completed regular-season LeagueGameLog T/P for the seasons in `NBA_IMPORT_SEASONS`):

```powershell
python -m splitedge_importer games-stats
```

Integration tests must use `splitedge_test`, never the primary `splitedge` database. Test-only minimum overrides are for pytest only; do not leave them set for a live import.

### Local verification

```powershell
cd C:\Users\kevng\OneDrive\Desktop\splitedge\backend

# Database-free: no PostgreSQL, no environment variables required.
mvn --batch-mode clean verify

# Optional: apply migrations to your own local database first.
$env:FLYWAY_URL = "jdbc:postgresql://localhost:5432/splitedge"
$env:FLYWAY_USER = "splitedge"
$env:FLYWAY_PASSWORD = "<your local password>"
mvn --batch-mode flyway:migrate

# Optional: guarded PostgreSQL integration suite, against splitedge_backend_test only.
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/splitedge_backend_test"
$env:SPRING_DATASOURCE_USERNAME = "splitedge"
$env:SPRING_DATASOURCE_PASSWORD = "<your local password>"
mvn --batch-mode -Ppostgres-it verify

cd C:\Users\kevng\OneDrive\Desktop\splitedge\importer
python -m pip install -e ".[dev]"
python -m ruff check .
python -m pytest -m "not integration"

$env:DATABASE_URL = "postgresql://splitedge:<your-local-password>@localhost:5432/splitedge_test"
$env:NBA_SEASON = "2025-26"
$env:NBA_IMPORT_SEASONS = "2023-24,2024-25,2025-26"
$env:IMPORT_MIN_TEAMS = "1"
$env:IMPORT_MIN_ACTIVE_PLAYERS = "1"
$env:IMPORT_MIN_GAMES_PER_SEASON = "1"
$env:IMPORT_MIN_PLAYER_STATS_PER_SEASON = "1"
python -m pytest -m integration
```

### Live historical import

Do not run this during automated tests or CI. Remove test-only guard overrides first, then point at the primary database:

```powershell
Remove-Item Env:IMPORT_MIN_TEAMS -ErrorAction SilentlyContinue
Remove-Item Env:IMPORT_MIN_ACTIVE_PLAYERS -ErrorAction SilentlyContinue
Remove-Item Env:IMPORT_MIN_GAMES_PER_SEASON -ErrorAction SilentlyContinue
Remove-Item Env:IMPORT_MIN_PLAYER_STATS_PER_SEASON -ErrorAction SilentlyContinue
$env:DATABASE_URL = "postgresql://splitedge:<your-local-password>@localhost:5432/splitedge"
$env:NBA_SEASON = "2025-26"
$env:NBA_IMPORT_SEASONS = "2023-24,2024-25,2025-26"
python -m splitedge_importer games-stats
```

## API (Milestone 2)

The backend serves every response from data already stored in PostgreSQL. It
never calls the NBA or any external service while handling a request — all
NBA data arrives separately, in advance, through the batch importer.

| Method & path | Purpose |
|---|---|
| `GET /api/data/status` | Whether stored data is ready to query (`READY`/`EMPTY`), the latest completed games/stats import, stored seasons, and row counts. |
| `GET /api/props` | The static catalog of every supported prop, in a fixed order, with the base stats each one sums. |
| `GET /api/players/{nbaPlayerId}` | Stored identity for one player by their canonical NBA player ID (works for inactive/historical players too). |
| `GET /api/teams` | Every stored team's identity, ordered by abbreviation. |
| `POST /api/reports/matchup` | The matchup report: a player's history against one opponent compared with their overall baseline, for one prop/line/direction. |

### Supported props

`POINTS`, `REBOUNDS`, `ASSISTS`, `THREE_POINTERS_MADE`, `PR` (points +
rebounds), `PA` (points + assists), `RA` (rebounds + assists), `PRA` (points +
rebounds + assists). `GET /api/props` is the source of truth for names, order,
and which base stats each combination prop sums.

### Matchup report request

```powershell
$body = @{
    nbaPlayerId       = 201939
    nbaOpponentTeamId = 1610612744
    prop              = "POINTS"
    line              = 27.5
    direction         = "OVER"
    recency           = "LAST_10"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reports/matchup `
    -ContentType "application/json" -Body $body
```

```bash
curl -X POST http://localhost:8080/api/reports/matchup \
  -H "Content-Type: application/json" \
  -d '{"nbaPlayerId":201939,"nbaOpponentTeamId":1610612744,"prop":"POINTS","line":27.5,"direction":"OVER","recency":"LAST_10"}'
```

Required fields: `nbaPlayerId`, `nbaOpponentTeamId` (positive NBA IDs), `prop`
(one of the names above), `line` (0–999, up to 3 decimal places), `direction`
(`OVER` or `UNDER`).

Optional filters and their defaults when omitted:

| Field | Values | Default |
|---|---|---|
| `season` | `YYYY-YY`, e.g. `2024-25` | every stored season |
| `location` | `HOME`, `AWAY`, `ALL` | `ALL` |
| `recency` | `LAST_5`, `LAST_10`, `LAST_20`, `ALL` | `ALL` (no game-count limit) |
| `minMinutes` | 0–80, up to 3 decimal places | `0` (no minimum) |

### Result and hit-rate definitions

- **OVER**: hits when the player's prop value for that game is strictly
  greater than `line`.
- **UNDER**: hits when the value is strictly less than `line`.
- **PUSH**: the value exactly equals `line` — this is neither a hit nor a
  miss. **Pushes are excluded from the hit-rate denominator**; hit rate is
  `hits / (hits + misses)`, so a push affects the raw counts you can inspect
  but never silently dilutes or inflates the percentage.
- **Matchup vs. baseline**: `matchup` is the player's qualifying games against
  the requested opponent only; `baseline` is the player's qualifying games
  overall, independent of opponent. Both apply the same `season`/`location`/
  `minMinutes` filters and the same `recency` limit independently, so they
  answer "how did this player do against this team" versus "how does this
  player usually do," under identical conditions.
- **Sample-quality label** (based on the matchup sample's qualifying game
  count): `LOW` for 1–4 games, `MODERATE` for 5–9 games, `HIGH` for 10 or more
  games. No qualifying games means no calculable hit rate at all.

### Response field overview

Rather than reproducing a full example payload here, the response groups are:

- `criteria` — the resolved player, opponent, prop, line, direction, and
  every filter actually applied (including defaults).
- `matchup` / `baseline` — each a `qualifyingGames`, `hits`, `misses`,
  `pushes`, `hitRate`, `average`, and `median`.
- `comparison.hitRateDifferencePoints` — matchup hit rate minus baseline hit
  rate, in percentage points.
- `sampleQuality` — `LOW`, `MODERATE`, `HIGH`, or absent when there is no
  qualifying sample.
- `dataFreshness` — the import type and completion timestamp backing this
  answer.
- `opponentContext` — always `{"available": false, "reason":
  "TEAM_DEFENSE_NOT_IMPORTED"}` today; team defensive context is Milestone 4.
- `games` / `chart` — every qualifying matchup game, in chronological order,
  each with its own result so nothing is asserted without supporting evidence.
- `warnings` — populated when a response deserves extra caution (for example,
  a very small sample).

### Error codes

All errors share one JSON shape (`code`, `message`, `fieldErrors`, `path`,
`timestamp`) and never include SQL, stack traces, credentials, or internal
paths.

| HTTP status | Example codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_JSON`, `UNKNOWN_PROPERTY`, `UNSUPPORTED_PROP`, `INVALID_LINE`, `INVALID_DIRECTION`, `INVALID_SEASON`, `INVALID_LOCATION`, `INVALID_RECENCY`, `INVALID_MIN_MINUTES`, `INVALID_PLAYER_ID` |
| 404 | `UNKNOWN_PLAYER`, `UNKNOWN_OPPONENT` |
| 405 / 415 | `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` |
| 500 | `DATA_INVARIANT_VIOLATION`, `INTERNAL_ERROR` |

### What this is not

Every hit rate is a historical count with its sample size and data-freshness
label attached — it is evidence, not a guarantee, a "lock," or a prediction of
future results. SplitEdge does not place wagers, connect to sportsbooks, or
serve live odds.

## Product rules

- Historical results are evidence, not guarantees.
- Every summary must be traceable to supporting games.
- Matchup performance must be compared with an overall baseline.
- Sample size and data freshness must remain visible.
- The MVP does not place wagers or connect to sportsbooks.

See [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md) for the complete specification.

## Responsible use

SplitEdge provides historical sports analytics for informational and educational purposes. Historical trends do not guarantee future results. SplitEdge does not place wagers or provide financial advice.
