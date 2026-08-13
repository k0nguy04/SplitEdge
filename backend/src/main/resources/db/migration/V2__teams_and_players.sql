ALTER TABLE import_runs
    ADD COLUMN import_type VARCHAR(64) NOT NULL DEFAULT 'TEAMS_PLAYERS',
    ADD COLUMN details JSONB;

CREATE TABLE teams (
    id              BIGSERIAL PRIMARY KEY,
    nba_team_id     BIGINT NOT NULL UNIQUE,
    abbreviation    VARCHAR(10) NOT NULL,
    full_name       VARCHAR(128) NOT NULL,
    nickname        VARCHAR(64) NOT NULL,
    city            VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE players (
    id              BIGSERIAL PRIMARY KEY,
    nba_player_id   BIGINT NOT NULL UNIQUE,
    first_name      VARCHAR(64) NOT NULL,
    last_name       VARCHAR(64) NOT NULL,
    full_name       VARCHAR(128) NOT NULL,
    is_active       BOOLEAN NOT NULL,
    nba_team_id     BIGINT NULL REFERENCES teams (nba_team_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX players_active_name_idx
    ON players (is_active, last_name, first_name);
