# SplitEdge

> Understand the matchup behind the prop.

SplitEdge is an NBA player-prop research platform that turns historical game data into transparent, opponent-specific matchup reports. The MVP will let a user select a player, opponent, prop, line, and direction, then compare the matchup result with the player's overall baseline and inspect every qualifying game.

## Repository status

Milestone 1 is in progress. The Python importer can load NBA teams, active
players, completed regular-season games, and MVP box-score stats into PostgreSQL.
Matchup reports are not in this slice.

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

## Quick start

1. Copy `.env.example` to `.env`.
2. Start PostgreSQL with `docker compose -f infra/compose.yaml up -d` if Docker is available.
3. Start the backend from `backend/` with `mvn spring-boot:run`.
4. Start the frontend from `frontend/` with `npm install` and `npm run dev`.
5. Apply database migrations from `backend/` with `mvn flyway:migrate`.
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
mvn --batch-mode flyway:migrate
mvn --batch-mode verify

cd C:\Users\kevng\OneDrive\Desktop\splitedge\importer
python -m pip install -e ".[dev]"
python -m ruff check .
python -m pytest -m "not integration"

$env:DATABASE_URL = "postgresql://splitedge:splitedge_local@localhost:5432/splitedge_test"
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
$env:DATABASE_URL = "postgresql://splitedge:splitedge_local@localhost:5432/splitedge"
$env:NBA_SEASON = "2025-26"
$env:NBA_IMPORT_SEASONS = "2023-24,2024-25,2025-26"
python -m splitedge_importer games-stats
```

## Product rules

- Historical results are evidence, not guarantees.
- Every summary must be traceable to supporting games.
- Matchup performance must be compared with an overall baseline.
- Sample size and data freshness must remain visible.
- The MVP does not place wagers or connect to sportsbooks.

See [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md) for the complete specification.

## Responsible use

SplitEdge provides historical sports analytics for informational and educational purposes. Historical trends do not guarantee future results. SplitEdge does not place wagers or provide financial advice.
