# SplitEdge

> Understand the matchup behind the prop.

SplitEdge is an NBA player-prop research platform that turns historical game data into transparent, opponent-specific matchup reports. The MVP will let a user select a player, opponent, prop, line, and direction, then compare the matchup result with the player's overall baseline and inspect every qualifying game.

## Repository status

Milestone 0 establishes the project structure and development guardrails. Product calculations and NBA data ingestion begin in Milestone 1.

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
5. Run the importer checks from `importer/` with `python -m pytest` after installing its development dependencies.

The application is designed to support a $0/month portfolio deployment. No paid sports feed, odds provider, or AI API is required.

## Product rules

- Historical results are evidence, not guarantees.
- Every summary must be traceable to supporting games.
- Matchup performance must be compared with an overall baseline.
- Sample size and data freshness must remain visible.
- The MVP does not place wagers or connect to sportsbooks.

See [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md) for the complete specification.

## Responsible use

SplitEdge provides historical sports analytics for informational and educational purposes. Historical trends do not guarantee future results. SplitEdge does not place wagers or provide financial advice.
