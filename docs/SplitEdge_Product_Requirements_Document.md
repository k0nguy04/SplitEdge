# SplitEdge Product Requirements Document

**Product:** SplitEdge  
**Tagline:** Understand the matchup behind the prop.  
**Document status:** MVP scope approved for planning  
**Version:** 1.0  
**Date:** August 13, 2026  

## 1. Product summary

SplitEdge is an NBA player-prop research platform that transforms historical game data into transparent, opponent-specific matchup reports. A user selects a player, opponent, prop statistic, line, and direction. SplitEdge then calculates how often the player exceeded or fell below that line in qualifying games, compares the matchup result with the player's overall baseline, and displays every game used in the calculation.

SplitEdge is a research and educational product. It does not place wagers, connect to sportsbooks, guarantee outcomes, or characterize a result as a sure bet.

## 2. Problem statement

NBA bettors frequently research player props using scattered game logs, general averages, and small recent-game samples. Existing workflows make it difficult to answer a complete matchup question while preserving context:

- How has the player performed against this opponent?
- How does that performance compare with the player's normal baseline?
- How large and recent is the sample?
- Which individual games produced the result?
- Does the opponent's defensive profile support or contradict the historical split?

SplitEdge brings these elements into one reproducible report and makes the supporting evidence visible.

## 3. Product goals

### 3.1 User goals

1. Generate an opponent-specific player-prop report in less than one minute.
2. Understand the difference between matchup performance and overall performance.
3. Inspect every game included in a calculation.
4. Recognize when a result is based on limited or outdated evidence.
5. Understand relevant opponent defensive context without interpreting it as a guarantee.

### 3.2 Portfolio goals

1. Demonstrate backend development with Java 21 and Spring Boot.
2. Demonstrate data ingestion and normalization with Python.
3. Demonstrate relational data modeling with PostgreSQL.
4. Demonstrate a responsive React and TypeScript interface.
5. Demonstrate tested statistical calculations, caching, automation, and observability.
6. Provide a public demonstration that a recruiter can understand within two minutes.
7. Operate at a target cost of $0 per month.

### 3.3 Non-goals

The MVP will not include:

- Live or in-game betting
- Sportsbook connections or automatic wagering
- Live odds or paid odds feeds
- Parlays or bet-slip generation
- Guaranteed predictions or “lock” language
- Machine-learning projections
- Individual defender tracking
- Injury-news aggregation
- User accounts, subscriptions, or payments
- Native mobile applications
- Sports outside the NBA
- Real-time game updates

## 4. Target user

The primary user is an NBA bettor who wants to research a player prop using opponent-specific historical evidence. The user understands basic NBA statistics but should not need statistical or programming expertise.

The secondary user is a recruiter or interviewer evaluating the engineering quality of the project.

## 5. Product principles

### 5.1 Transparent

Every calculated result must be traceable to the games used to produce it.

### 5.2 Contextual

Matchup performance must be compared with the player's overall baseline. A matchup hit rate must never appear alone.

### 5.3 Honest about uncertainty

The product must display sample size, data freshness, and limitations prominently.

### 5.4 Fast to understand

The most important result should be visible before the user examines charts or detailed tables.

### 5.5 Cost controlled

No MVP requirement may depend on a paid sports feed, paid AI API, always-running worker, or metered cloud plan.

## 6. MVP scope

### 6.1 League and seasons

- NBA only
- Active players
- Current season and previous two completed seasons
- Completed regular-season games
- Playoff analysis deferred until after MVP

### 6.2 Supported props

Initial individual props:

- Points
- Rebounds
- Assists
- Three-pointers made

Initial combination props:

- Points + rebounds
- Points + assists
- Rebounds + assists
- Points + rebounds + assists

Later props may include steals, blocks, turnovers, field-goal attempts, and three-point attempts.

### 6.3 Supported filters

Required inputs:

- Player
- Opponent
- Prop
- Prop line
- Direction: over or under

Optional MVP filters:

- Season selection
- Home, away, or all games
- Last 5, 10, 20, or all qualifying games
- Minimum minutes played

Deferred filters:

- Days of rest
- Back-to-back games
- With or without a teammate
- Starting lineup combinations
- Individual primary defender
- Game pace ranges
- Opponent defensive-rank ranges

## 7. Primary user journey

1. The user opens Prop Research.
2. The user searches for and selects an active NBA player.
3. The user selects an opposing team.
4. The user selects a prop statistic.
5. The user enters a numeric line, such as 27.5.
6. The user selects over or under.
7. The user optionally changes season, location, recency, or minimum-minutes filters.
8. The user selects **Generate Report**.
9. SplitEdge validates the request and generates the report.
10. The user reviews the summary, baseline comparison, chart, opponent context, and supporting games.

## 8. Screen requirements

### 8.1 Home page

#### Purpose

Explain the product immediately and give visitors a fast route to a working example.

#### Required content

- SplitEdge name and tagline
- One-sentence product explanation
- **Research a matchup** primary action
- **View example report** secondary action
- Pre-generated example report summary
- Three principles: transparent, contextual, responsible
- Data freshness indicator
- Responsible-use notice in the footer

#### Acceptance criteria

- A first-time visitor can describe the product after viewing the page for 15 seconds.
- The example report remains visible even when the free backend is waking from sleep.
- No language implies guaranteed future performance.

### 8.2 Prop Research page

#### Required controls

- Searchable player selector
- Opponent team selector
- Prop selector
- Numeric line input
- Over/under selector
- Season selector
- Home/away/all selector
- Recent qualifying games selector
- Minimum-minutes input
- Generate Report button
- Reset Filters button

#### Validation

- A player must be selected.
- An opponent must be selected.
- The selected opponent cannot be the player's current team for a current-season query.
- The line must be numeric and zero or greater.
- At least one season must be selected.
- Minimum minutes must be between 0 and 48.
- Invalid fields must show specific, accessible messages.

#### Loading behavior

- The form must indicate that a report is being generated.
- Duplicate submissions must be prevented while a request is active.
- A friendly message must explain that the free demo may take up to approximately one minute to wake.

#### Empty and error states

- No qualifying games: explain which filters produced no results and offer a reset.
- Stale data: show the last successful update time.
- API unavailable: preserve form selections and allow retry.
- Partial opponent context: display the historical report and label the unavailable section.

### 8.3 Matchup Report page

#### Summary section

Display:

- Player and opponent
- Prop, line, and direction
- Historical matchup hit rate
- Overall baseline hit rate
- Percentage-point difference
- Qualifying-game count
- Matchup average
- Matchup median
- Sample-quality label
- Data last updated time

The primary sentence should follow a factual template:

> [Player] finished [over/under] [line] [prop] in [hits] of [games] qualifying games against [opponent].

#### Comparison section

Display matchup and baseline values side by side:

- Hit rate
- Average
- Median
- Average minutes
- Number of games

#### Trend chart

- One mark or bar per qualifying game
- Horizontal prop-line reference
- Distinct over, under, and push states
- Date and value on hover or focus
- Accessible text alternative
- Games ordered chronologically

#### Opponent context

Display when available:

- Defensive rating and league rank
- Pace and league rank
- Selected statistic allowed and league rank
- Recent defensive trend
- Explanation of the measurement period

Opponent context must not be included in the historical hit-rate calculation.

#### Supporting game log

Each row must include:

- Date
- Home or away
- Opponent
- Minutes
- Selected statistic
- Prop line
- Over, under, or push result
- Final game score

The table must support sorting by date and selected-statistic value.

#### Acceptance criteria

- All visible summary values match the supporting games.
- The matchup hit rate and baseline hit rate are clearly distinguishable.
- The report never hides qualifying games that contradict the headline result.
- The report identifies limited samples.
- The report URL can be refreshed without losing the query when practical.

### 8.4 Player page

#### MVP content

- Player name, position, and current team
- Current-season averages for supported props
- Recent game log
- Opponent matchup table
- Strongest and weakest historical matchup differences, labeled as descriptive history rather than predictions

### 8.5 Team Defense page

#### MVP content

- Team identity
- Defensive rating
- Pace
- Supported statistics allowed
- League ranks
- Recent trend
- Measurement period and data freshness

### 8.6 Methodology page

The page must explain:

- Data source and update schedule
- Included seasons and games
- Qualifying-game rules
- Over, under, and push rules
- Combination-prop formulas
- Baseline definition
- Sample-quality labels
- Matchup Score formula if enabled
- Known limitations
- Responsible-use statement

## 9. Calculation rules

### 9.1 Qualifying matchup game

A game qualifies when all of the following are true:

1. The selected player recorded game statistics.
2. The game is completed.
3. The opponent matches the selected team.
4. The season is selected.
5. The game matches the selected location filter.
6. The player's minutes meet the minimum-minutes filter.
7. The game is within the selected recency limit after all other filters are applied.

### 9.2 Prop value

Individual prop values use the corresponding stored box-score field.

Combination props are computed when the report is generated:

- PR = points + rebounds
- PA = points + assists
- RA = rebounds + assists
- PRA = points + rebounds + assists

Combination values must not be stored as duplicate database fields.

### 9.3 Result classification

For an over query:

- Over hit when value > line
- Miss when value < line
- Push when value = line

For an under query:

- Under hit when value < line
- Miss when value > line
- Push when value = line

Pushes are excluded from hit-rate denominator calculations and reported separately. Half-point lines cannot result in pushes.

### 9.4 Hit rate

`hit rate = hits / (hits + misses) × 100`

The UI must also display raw counts. Percentages should be rounded to one decimal place for display while calculations retain full precision.

### 9.5 Overall baseline

The baseline uses the same player, prop, line, direction, selected seasons, location, recency, and minimum-minutes rules, but includes all opponents.

The selected opponent games remain part of the overall baseline because it represents the player's complete performance under the other selected conditions.

### 9.6 Average and median

- Average uses the arithmetic mean of qualifying prop values.
- Median uses the middle sorted value, or the mean of the two middle values for an even number of games.
- Values display with one decimal place.

### 9.7 Sample quality

Initial labels:

- 1–4 qualifying games: Very limited
- 5–9: Limited
- 10–19: Moderate
- 20 or more: Strong historical sample

Zero qualifying games produces no score or percentage. A later version may reduce confidence when most games are from older seasons.

### 9.8 Matchup Score

The Matchup Score is optional for the first release. It must not launch until every component is documented and tested. If included, it must be labeled as a descriptive historical score rather than a probability.

Proposed components:

- Opponent-specific historical performance: 30%
- Overall baseline: 20%
- Recent form: 20%
- Opponent defensive context: 20%
- Sample quality: 10%

## 10. Data requirements

### 10.1 Core entities

- Player
- Team
- Game
- PlayerGameStat
- TeamGameStat
- ImportRun

### 10.2 Data integrity

- Players, teams, and games must retain stable external identifiers.
- A player-game statistic must be unique by player and game.
- Historical player-game rows must retain the team the player represented in that game.
- Imports must be idempotent.
- A failed import must not delete or corrupt previously valid data.
- Raw external values must be normalized before entering analytics tables.
- Every import must record start time, completion time, status, processed count, failure count, and error details.

### 10.3 Data freshness

- Completed game data should refresh once daily during the NBA season.
- The application must show the timestamp of the last successful import.
- Data more than 48 hours old during the active season should be labeled potentially stale.
- Users must still be able to query previously imported data if an update fails.

### 10.4 Data-source protection

- Rate-limit external requests.
- Retry transient failures with capped exponential backoff.
- Store local fixture responses for tests.
- Never call the external NBA source while serving a user report.
- Avoid storing player photos, team logos, or video in the MVP.
- Document attribution and data limitations.

## 11. Technical architecture

### 11.1 Components

- **Frontend:** React and TypeScript
- **Backend:** Java 21 and Spring Boot
- **Importer:** Python and pandas, using `nba_api` initially
- **Database:** PostgreSQL
- **Cache:** Caffeine in-process cache
- **Automation:** GitHub Actions scheduled and manual workflows
- **Local infrastructure:** Docker Compose where supported
- **Observability:** Spring Boot Actuator, structured logs, import status, and local Prometheus/Grafana as an optional demonstration

### 11.2 Architectural style

The MVP will use a modular monolith rather than microservices. Backend modules should separate:

- Players
- Teams
- Games
- Reports
- Imports and data status
- Shared error handling

The Python importer is a separate executable pipeline because it has a distinct language and lifecycle. It is not an always-running service.

### 11.3 Initial API surface

- `GET /api/players?search=`
- `GET /api/players/{id}`
- `GET /api/players/{id}/games`
- `GET /api/teams`
- `GET /api/teams/{id}/defense`
- `GET /api/props`
- `POST /api/reports/matchup`
- `GET /api/data/status`
- `GET /actuator/health`

### 11.4 Report request

The report request must include:

- Player identifier
- Opponent identifier
- Prop type
- Numeric line
- Direction
- One or more seasons
- Location filter
- Recency limit or all
- Minimum minutes

### 11.5 Report response

The response must include:

- Normalized request criteria
- Matchup summary statistics
- Baseline summary statistics
- Difference values
- Sample-quality label
- Push count
- Supporting matchup games
- Opponent context when available
- Data freshness timestamp
- Warnings

## 12. Cost and deployment requirements

### 12.1 Target budget

- Local development: $0
- Public portfolio demonstration: $0 per month
- Optional custom domain: deferred until the product is complete

### 12.2 Planned free deployment

- Frontend: Cloudflare Pages or equivalent free static hosting
- Backend: Render free web service or equivalent
- Database: Neon PostgreSQL Free or equivalent
- Scheduled imports: GitHub Actions in a public repository
- Source control and CI: GitHub

### 12.3 Cost guardrails

- No paid sports data API
- No paid AI API
- No paid odds feed
- No always-running Python worker
- No hosted Redis requirement
- No hosted Prometheus/Grafana requirement
- No service that automatically creates usage charges
- Document all free-tier limitations
- Maintain a local development path for every hosted dependency

### 12.4 Free-tier user experience

Because a free backend may sleep during inactivity:

- The frontend must show a clear wake-up message.
- The home page must include a static example that does not require the API.
- API requests should use a reasonable timeout and provide retry.
- The README and demo video should explain cold-start behavior.

## 13. Security and privacy

- No sportsbook credentials or payment information will be collected.
- No user account data will be collected in the MVP.
- Secrets and database credentials must use environment variables.
- Secrets must never be committed to version control.
- API inputs must be validated server-side.
- Database access must use parameterized queries through the persistence framework.
- CORS must allow only approved frontend origins in deployment.
- Error responses must not expose stack traces or credentials.
- Dependency and secret scanning should run in CI when available at no cost.

## 14. Accessibility and responsive design

- Support keyboard navigation.
- Associate labels and validation messages with form controls.
- Do not use color as the only indication of over, under, or warning status.
- Provide a text alternative for charts.
- Meet reasonable contrast standards.
- Support desktop and mobile widths.
- Tables may scroll horizontally on small screens without breaking the page.
- Respect reduced-motion preferences.

## 15. Observability

The application should expose or record:

- Health status
- Request count
- Request latency
- Error count
- Cache hit and miss counts
- Report-generation time
- Last successful import
- Import duration
- Records processed and rejected
- Structured error logs with correlation identifiers

Prometheus and Grafana may run locally for demonstration but are not required to be hosted continuously.

## 16. Testing strategy

### 16.1 Importer tests

- Normalizes source fields correctly
- Handles missing values
- Retries transient errors
- Prevents duplicate player-game records
- Records failed imports
- Produces stable results from fixture data

### 16.2 Backend unit tests

- Over calculation
- Under calculation
- Push exclusion
- Combination props
- Baseline calculation
- Season filter
- Location filter
- Recency filter
- Minimum-minutes filter
- Average and median
- Sample-quality labels
- Empty-result behavior

### 16.3 Backend integration tests

- API validation
- Database queries
- Report response structure
- Duplicate constraints
- Health and data-status endpoints

### 16.4 Frontend tests

- Form validation
- Loading state
- Empty state
- Error and retry state
- Summary rendering
- Chart data transformation
- Supporting game table
- Keyboard navigation of primary workflow

### 16.5 End-to-end test

Given seeded fixture data, a user must be able to select a player, opponent, prop, line, and direction and receive the expected report values.

## 17. Performance requirements

- Player search should respond within 300 milliseconds under normal demo conditions after backend wake-up.
- A cached matchup report should respond within 500 milliseconds.
- An uncached report should target less than two seconds for the MVP dataset.
- The API must paginate large game-log responses.
- Common report queries should use database indexes on player, opponent, season, and game date.
- External data retrieval must never be part of report-response latency.

## 18. Analytics and success measures

No paid product-analytics service is required. Privacy-conscious, local application metrics are sufficient.

MVP measures:

- Successful report-generation rate
- Report-generation latency
- Empty-result rate
- Import success rate
- Data freshness
- Number of automated calculation tests

Portfolio success measures:

- Public demo is accessible.
- README explains architecture and tradeoffs.
- Two-minute demo video shows the complete workflow.
- Core calculations have automated coverage.
- Project runs locally with documented commands.
- No mandatory recurring cost is introduced.

## 19. Delivery milestones

### Milestone 0: Repository foundation

Deliverables:

- Monorepo structure
- Backend, frontend, importer, and infrastructure directories
- Environment-variable examples
- Formatting and linting configuration
- CI skeleton
- Cursor project rules
- Architecture and contribution documentation

Exit criteria:

- Each component builds independently.
- No secrets are committed.
- A new developer can understand the repository structure.

### Milestone 1: Data proof of concept

Deliverables:

- Import teams, active players, games, and supported player statistics
- PostgreSQL schema and migrations
- Import-run records
- Fixture-based importer tests

Exit criteria:

- Importing the same player and games twice creates no duplicates.
- A known player-opponent result can be verified manually against source data.

### Milestone 2: Analytics engine

Deliverables:

- Player and team endpoints
- Matchup report endpoint
- Individual and combination prop calculations
- Baseline comparison
- Sample-quality label
- Comprehensive calculation tests

Exit criteria:

- Fixture-based expected values match API output.
- Invalid requests return specific errors.
- Empty results do not produce misleading percentages.

### Milestone 3: Matchup interface

Deliverables:

- Prop Research form
- Matchup summary
- Baseline comparison
- Trend chart
- Supporting game table
- Loading, empty, stale, and error states
- Responsive design

Exit criteria:

- A new user completes the primary journey without assistance.
- Displayed values match API fixture results.

### Milestone 4: Opponent context

Deliverables:

- Team defensive statistics
- League rankings
- Opponent context panel
- Team Defense page
- Methodology documentation

Exit criteria:

- Every context statistic has a documented definition and period.
- Context is visually separated from historical hit-rate calculations.

### Milestone 5: Automation and reliability

Deliverables:

- Scheduled and manual imports
- Retry and failure reporting
- Data-freshness warnings
- Caching
- Health and metrics
- CI test suite

Exit criteria:

- A failed update preserves existing usable data.
- Import status identifies the failing stage.
- The application reports stale data correctly.

### Milestone 6: Portfolio launch

Deliverables:

- Free public frontend, backend, and database deployment
- Static example report
- Polished README
- Architecture diagram
- Screenshots
- Two-minute demonstration video
- Load-test summary
- Design decisions and limitations

Exit criteria:

- A recruiter can open the application and understand it within two minutes.
- The demonstration does not require a paid account or paid API.
- The project can be run locally from documented steps.

## 20. Launch checklist

- [ ] All MVP calculations are documented and tested.
- [ ] Supporting games reconcile with summary values.
- [ ] Baseline comparison is always displayed.
- [ ] Sample size and data freshness are visible.
- [ ] No guaranteed-outcome language appears.
- [ ] Responsible-use notice is present.
- [ ] Methodology and data limitations are public.
- [ ] Mobile and keyboard workflows are usable.
- [ ] Secrets and credentials are absent from the repository.
- [ ] Public services remain within free plans.
- [ ] Static example works during backend cold starts.
- [ ] README and demo video are complete.

## 21. Deferred roadmap

Only consider these after all launch criteria are met:

1. Playoff filters
2. Days-of-rest and back-to-back splits
3. With-or-without-teammate splits
4. Injury and expected-minutes context
5. Additional props
6. Explainable Matchup Score
7. Saved reports using browser storage
8. Shareable report URLs
9. Additional NBA seasons
10. NFL, MLB, or NHL support through sport-specific data adapters

## 22. Responsible-use statement

SplitEdge provides historical sports analytics for informational and educational purposes. Historical trends do not guarantee future results. SplitEdge does not place wagers or provide financial advice. Users are responsible for complying with applicable laws and wagering responsibly.

## 23. Final scope decision

The first release is successful when a user can select any imported active NBA player, choose an opponent and supported prop, enter a line, apply the supported filters, and receive a correct, explainable report containing matchup performance, overall baseline, sample quality, opponent context, and every supporting game. All required development and public demonstration infrastructure must remain free to use within documented limits.
