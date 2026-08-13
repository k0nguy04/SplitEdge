"""Completed regular-season games and player box-score import pipeline."""

from __future__ import annotations

from collections import Counter
from typing import Any

from splitedge_importer.config import Config
from splitedge_importer.guards_games import evaluate_games_guards
from splitedge_importer.models import (
    AmbiguousGame,
    EntityCounts,
    ImportResult,
    NormalizedGame,
    NormalizedPlayerGameStat,
    RejectedRecord,
)
from splitedge_importer.normalization.games import complete_ambiguous_game, normalize_games
from splitedge_importer.normalization.player_stats import (
    normalize_player_stats,
    stubs_for_unknown_players,
)
from splitedge_importer.persistence.checkpoints import (
    reusable_fetched_rows,
    reusable_fetched_summary,
)
from splitedge_importer.persistence.store import GamesImportStore, PostgresImportStore
from splitedge_importer.redact import json_safe, redact_text
from splitedge_importer.retrieval.box_score_summary import (
    HomeAwayResolverError,
    summary_resource,
    validate_game_summary,
)
from splitedge_importer.retrieval.client import NbaSource
from splitedge_importer.validation.records import parse_positive_id

IMPORT_TYPE = "GAMES_STATS"
TEAM_RESOURCE = "team_game_log"
PLAYER_RESOURCE = "player_game_log"


def run_games_pipeline(
    config: Config,
    *,
    source: NbaSource,
    store: GamesImportStore | None = None,
) -> ImportResult:
    data_store = store or PostgresImportStore(config.database_url)
    run_id = data_store.insert_running(IMPORT_TYPE)
    secrets = [config.database_url, config.password]
    seasons = config.import_seasons
    details = _empty_details(seasons)

    try:
        details["stage"] = "load_teams"
        known_team_ids = data_store.list_team_ids()
        if len(known_team_ids) < config.min_teams:
            details["guard"] = {"passed": False, "reason": "teams_below_minimum"}
            return _fail(
                data_store,
                run_id,
                records_failed=0,
                details=details,
                error_message="import batch failed guard: teams_below_minimum",
                secrets=secrets,
            )

        raw_by_season: dict[str, dict[str, list[dict[str, Any]]]] = {}
        checkpoints_reused = 0
        http_by_season: dict[str, int] = {season: 0 for season in seasons}
        used_checkpoints: list[tuple[str, str]] = []

        for season in seasons:
            season_raw: dict[str, list[dict[str, Any]]] = {}
            for resource, fetcher in (
                (TEAM_RESOURCE, source.fetch_team_game_log),
                (PLAYER_RESOURCE, source.fetch_player_game_log),
            ):
                details["stage"] = f"retrieve_{resource}"
                checkpoint = data_store.load_checkpoint(IMPORT_TYPE, season, resource)
                reused = reusable_fetched_rows(
                    checkpoint,
                    import_type=IMPORT_TYPE,
                    season=season,
                    resource=resource,
                )
                if reused is not None:
                    season_raw[resource] = reused
                    checkpoints_reused += 1
                    used_checkpoints.append((season, resource))
                    continue
                rows = fetcher(season)
                if not rows:
                    raise RuntimeError(f"empty {resource} for {season}")
                data_store.save_fetched_checkpoint(
                    import_type=IMPORT_TYPE,
                    season=season,
                    resource=resource,
                    rows=rows,
                )
                season_raw[resource] = rows
                http_by_season[season] += 1
                used_checkpoints.append((season, resource))
            raw_by_season[season] = season_raw

        details["stage"] = "validate"
        games: list[NormalizedGame] = []
        stats: list[NormalizedPlayerGameStat] = []
        rejected: list[RejectedRecord] = []
        skipped_dnp = 0
        skipped_non_regular = 0
        duplicate_game_ids = False
        duplicate_stat_ids = False
        games_received = 0
        stats_received = 0
        season_player_rows: dict[str, list[dict[str, Any]]] = {}

        for season in seasons:
            team_rows = raw_by_season[season][TEAM_RESOURCE]
            player_rows = raw_by_season[season][PLAYER_RESOURCE]
            season_player_rows[season] = player_rows
            games_received += len(team_rows)
            stats_received += len(player_rows)
            season_games, season_ambiguous, game_rejected, game_skipped, game_dupes = (
                normalize_games(
                    team_rows,
                    season=season,
                    known_team_ids=known_team_ids,
                )
            )
            resolved = _resolve_ambiguous_games(
                season_ambiguous,
                source=source,
                store=data_store,
                details=details,
                http_by_season=http_by_season,
                used_checkpoints=used_checkpoints,
            )
            checkpoints_reused += resolved.reused
            games.extend(season_games)
            games.extend(resolved.games)
            rejected.extend(game_rejected)
            skipped_non_regular += game_skipped
            duplicate_game_ids = duplicate_game_ids or game_dupes

        for season in seasons:
            season_stats, stat_rejected, dnp, stat_skipped, stat_dupes = normalize_player_stats(
                season_player_rows[season],
                season=season,
                games=games,
                known_team_ids=known_team_ids,
            )
            stats.extend(season_stats)
            rejected.extend(stat_rejected)
            skipped_dnp += dnp
            skipped_non_regular += stat_skipped
            duplicate_stat_ids = duplicate_stat_ids or stat_dupes

        known_player_ids = data_store.list_player_ids()
        stubs = stubs_for_unknown_players(stats, known_player_ids)
        records_failed = len(rejected)
        details = _build_details(
            seasons=seasons,
            stage="validate",
            games_received=games_received,
            games_persisted=0,
            games_rejected=sum(1 for item in rejected if item.entity == "game"),
            stats_received=stats_received,
            stats_persisted=0,
            stats_rejected=sum(1 for item in rejected if item.entity == "player_stat"),
            skipped_dnp=skipped_dnp,
            skipped_non_regular=skipped_non_regular,
            checkpoints_reused=checkpoints_reused,
            historical_players_inserted=0,
            rejected=rejected,
            http_by_season=http_by_season,
            games=games,
            stats=stats,
        )

        guard = evaluate_games_guards(
            team_count=len(known_team_ids),
            min_teams=config.min_teams,
            games=games,
            stats=stats,
            known_team_ids=known_team_ids,
            duplicate_game_ids=duplicate_game_ids,
            duplicate_stat_ids=duplicate_stat_ids,
            seasons=seasons,
            min_games_per_season=config.min_games_per_season,
            min_player_stats_per_season=config.min_player_stats_per_season,
        )
        details["guard"] = {"passed": guard.passed, "reason": guard.reason}
        if not guard.passed:
            details["stage"] = "persist_guard"
            return _fail(
                data_store,
                run_id,
                records_failed=records_failed,
                details=details,
                error_message=f"import batch failed guard: {guard.reason}",
                secrets=secrets,
            )

        details["stage"] = "persist"
        persist_details = _build_details(
            seasons=seasons,
            stage="persist",
            games_received=games_received,
            games_persisted=len(games),
            games_rejected=sum(1 for item in rejected if item.entity == "game"),
            stats_received=stats_received,
            stats_persisted=len(stats),
            stats_rejected=sum(1 for item in rejected if item.entity == "player_stat"),
            skipped_dnp=skipped_dnp,
            skipped_non_regular=skipped_non_regular,
            checkpoints_reused=checkpoints_reused,
            historical_players_inserted=0,
            rejected=rejected,
            http_by_season=http_by_season,
            games=games,
            stats=stats,
            guard=guard.passed,
            guard_reason=guard.reason,
        )
        records_processed = len(games) + len(stats)
        inserted = data_store.persist_games_and_complete(
            run_id,
            stubs=stubs,
            games=games,
            stats=stats,
            seasons=seasons,
            used_checkpoints=tuple(dict.fromkeys(used_checkpoints)),
            records_processed=records_processed,
            records_failed=records_failed,
            details=persist_details,
        )
        persist_details["historical_players_inserted"] = inserted
        return ImportResult(
            success=True,
            run_id=run_id,
            status="COMPLETED",
            records_processed=records_processed,
            records_failed=records_failed,
            details=persist_details,
        )
    except HomeAwayResolverError as exc:
        details["stage"] = "retrieve_box_score_summary"
        details["resolver"] = {
            "reason": exc.reason,
            "season": exc.season,
            "nba_game_id": exc.nba_game_id,
        }
        return _fail(
            data_store,
            run_id,
            records_failed=int(details.get("games", {}).get("rejected", 0))
            + int(details.get("player_stats", {}).get("rejected", 0)),
            details=details,
            error_message=str(exc),
            secrets=secrets,
        )
    except Exception as exc:
        details["stage"] = details.get("stage") or "retrieve_team_game_log"
        details.setdefault("guard", {"passed": False, "reason": None})
        return _fail(
            data_store,
            run_id,
            records_failed=int(details.get("games", {}).get("rejected", 0))
            + int(details.get("player_stats", {}).get("rejected", 0)),
            details=details,
            error_message=str(exc),
            secrets=secrets,
        )


class _ResolvedAmbiguous:
    def __init__(self, games: list[NormalizedGame], reused: int) -> None:
        self.games = games
        self.reused = reused


def _resolve_ambiguous_games(
    ambiguous_games: list[AmbiguousGame],
    *,
    source: NbaSource,
    store: GamesImportStore,
    details: dict[str, Any],
    http_by_season: dict[str, int],
    used_checkpoints: list[tuple[str, str]],
) -> _ResolvedAmbiguous:
    resolved: list[NormalizedGame] = []
    reused_count = 0
    for ambiguous in ambiguous_games:
        details["stage"] = "retrieve_box_score_summary"
        resource = summary_resource(ambiguous.nba_game_id)
        log_team_ids = {
            team_id
            for team_id in (
                parse_positive_id(row.get("TEAM_ID")) for row in ambiguous.team_rows
            )
            if team_id is not None
        }
        checkpoint = store.load_checkpoint(IMPORT_TYPE, ambiguous.season, resource)
        summary = reusable_fetched_summary(
            checkpoint,
            import_type=IMPORT_TYPE,
            season=ambiguous.season,
            resource=resource,
            requested_game_id=ambiguous.nba_game_id,
            log_team_ids=log_team_ids,
        )
        if summary is None:
            try:
                raw = source.fetch_box_score_summary(ambiguous.nba_game_id)
            except HomeAwayResolverError:
                raise
            except Exception as exc:
                raise HomeAwayResolverError(
                    "network_failure",
                    season=ambiguous.season,
                    nba_game_id=ambiguous.nba_game_id,
                ) from exc
            summary = validate_game_summary(
                raw,
                requested_game_id=ambiguous.nba_game_id,
                log_team_ids=log_team_ids,
                season=ambiguous.season,
            )
            store.save_fetched_checkpoint(
                import_type=IMPORT_TYPE,
                season=ambiguous.season,
                resource=resource,
                payload=summary,
            )
            http_by_season[ambiguous.season] = http_by_season.get(ambiguous.season, 0) + 1
        else:
            reused_count += 1
        used_checkpoints.append((ambiguous.season, resource))
        resolved.append(
            complete_ambiguous_game(
                ambiguous,
                summary["homeTeamId"],
                summary["awayTeamId"],
            )
        )
    return _ResolvedAmbiguous(resolved, reused_count)


def _fail(
    store: GamesImportStore,
    run_id: int,
    *,
    records_failed: int,
    details: dict[str, Any],
    error_message: str,
    secrets: list[str | None],
) -> ImportResult:
    safe_message = redact_text(error_message, secrets)
    safe_details = json_safe(details)
    store.mark_failed(
        run_id,
        records_processed=0,
        records_failed=records_failed,
        error_message=safe_message,
        details=safe_details,
    )
    return ImportResult(
        success=False,
        run_id=run_id,
        status="FAILED",
        records_processed=0,
        records_failed=records_failed,
        details=safe_details,
        error_message=safe_message,
    )


def _empty_details(seasons: tuple[str, ...]) -> dict[str, Any]:
    return _build_details(
        seasons=seasons,
        stage="load_teams",
        games_received=0,
        games_persisted=0,
        games_rejected=0,
        stats_received=0,
        stats_persisted=0,
        stats_rejected=0,
        skipped_dnp=0,
        skipped_non_regular=0,
        checkpoints_reused=0,
        historical_players_inserted=0,
        rejected=[],
        http_by_season={season: 0 for season in seasons},
        games=[],
        stats=[],
    )


def _build_details(
    *,
    seasons: tuple[str, ...],
    stage: str,
    games_received: int,
    games_persisted: int,
    games_rejected: int,
    stats_received: int,
    stats_persisted: int,
    stats_rejected: int,
    skipped_dnp: int,
    skipped_non_regular: int,
    checkpoints_reused: int,
    historical_players_inserted: int,
    rejected: list[RejectedRecord],
    http_by_season: dict[str, int],
    games: list[NormalizedGame],
    stats: list[NormalizedPlayerGameStat],
    guard: bool | None = None,
    guard_reason: str | None = None,
) -> dict[str, Any]:
    games_by_season = Counter(game.season for game in games)
    stats_by_season = Counter(stat.season for stat in stats)
    return json_safe(
        {
            "seasons": list(seasons),
            "stage": stage,
            "games": EntityCounts(
                received=games_received,
                persisted=games_persisted,
                rejected=games_rejected,
                warning=0,
                deactivation=0,
            ).as_dict(),
            "player_stats": EntityCounts(
                received=stats_received,
                persisted=stats_persisted,
                rejected=stats_rejected,
                warning=0,
                deactivation=0,
            ).as_dict(),
            "historical_players_inserted": historical_players_inserted,
            "skipped_dnp": skipped_dnp,
            "skipped_non_regular": skipped_non_regular,
            "checkpoints_reused": checkpoints_reused,
            "by_season": {
                season: {
                    "games_persisted": games_by_season[season],
                    "stats_persisted": stats_by_season[season],
                    "http_calls": http_by_season.get(season, 0),
                }
                for season in seasons
            },
            "rejected": [
                {
                    "reason": item.reason,
                    "entity": item.entity,
                    "raw": item.raw,
                    "season": item.season,
                    "nba_game_id": item.nba_game_id,
                }
                for item in rejected
            ],
            "guard": {"passed": guard, "reason": guard_reason},
        }
    )
