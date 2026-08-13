CREATE TABLE games (
    id                 BIGSERIAL PRIMARY KEY,
    nba_game_id        VARCHAR(16) NOT NULL UNIQUE,
    season             VARCHAR(8)  NOT NULL,
    game_date          DATE        NOT NULL,
    home_nba_team_id   BIGINT      NOT NULL REFERENCES teams (nba_team_id),
    away_nba_team_id   BIGINT      NOT NULL REFERENCES teams (nba_team_id),
    home_score         INTEGER     NOT NULL,
    away_score         INTEGER     NOT NULL,
    status             VARCHAR(16) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT games_status_check CHECK (status = 'FINAL'),
    CONSTRAINT games_distinct_teams CHECK (home_nba_team_id <> away_nba_team_id),
    CONSTRAINT games_scores_nonnegative CHECK (home_score >= 0 AND away_score >= 0)
);

CREATE INDEX games_season_date_idx ON games (season, game_date);
CREATE INDEX games_home_team_idx ON games (home_nba_team_id, season);
CREATE INDEX games_away_team_idx ON games (away_nba_team_id, season);

CREATE TABLE player_game_stats (
    id                   BIGSERIAL PRIMARY KEY,
    nba_game_id          VARCHAR(16) NOT NULL REFERENCES games (nba_game_id),
    nba_player_id        BIGINT      NOT NULL REFERENCES players (nba_player_id),
    nba_team_id          BIGINT      NOT NULL REFERENCES teams (nba_team_id),
    minutes              NUMERIC(7,3) NOT NULL,
    points               INTEGER     NOT NULL,
    rebounds             INTEGER     NOT NULL,
    assists              INTEGER     NOT NULL,
    three_pointers_made  INTEGER     NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT player_game_stats_unique UNIQUE (nba_player_id, nba_game_id),
    CONSTRAINT player_game_stats_minutes_positive CHECK (minutes > 0),
    CONSTRAINT player_game_stats_counts_nonnegative CHECK (
        points >= 0 AND rebounds >= 0 AND assists >= 0 AND three_pointers_made >= 0
    )
);

CREATE INDEX player_game_stats_game_idx ON player_game_stats (nba_game_id);
CREATE INDEX player_game_stats_team_game_idx ON player_game_stats (nba_team_id, nba_game_id);

CREATE TABLE import_checkpoints (
    id           BIGSERIAL PRIMARY KEY,
    import_type  VARCHAR(64) NOT NULL,
    season       VARCHAR(8)  NOT NULL,
    resource     VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    row_count    INTEGER     NOT NULL DEFAULT 0,
    payload      JSONB,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT import_checkpoints_unique UNIQUE (import_type, season, resource),
    CONSTRAINT import_checkpoints_status_check CHECK (status IN ('FETCHED', 'PERSISTED'))
);
