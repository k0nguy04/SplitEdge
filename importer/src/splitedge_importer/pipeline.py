"""Teams-and-active-players import pipeline."""

from __future__ import annotations

from typing import Any

from splitedge_importer.config import Config
from splitedge_importer.guards import evaluate_guards
from splitedge_importer.models import (
    EntityCounts,
    ImportResult,
    RejectedRecord,
    WarningRecord,
)
from splitedge_importer.normalization.players import normalize_players
from splitedge_importer.normalization.teams import normalize_teams
from splitedge_importer.persistence.store import ImportStore, PostgresImportStore
from splitedge_importer.redact import json_safe, redact_text
from splitedge_importer.retrieval.client import NbaSource


def run_pipeline(
    config: Config,
    *,
    source: NbaSource,
    store: ImportStore | None = None,
) -> ImportResult:
    data_store = store or PostgresImportStore(config.database_url)
    run_id = data_store.insert_running("TEAMS_PLAYERS")
    secrets = [config.database_url, config.password]
    details = _empty_details(config.nba_season)

    try:
        details["stage"] = "retrieve_teams"
        teams_raw = source.fetch_teams()
        details["stage"] = "retrieve_players"
        players_raw = source.fetch_active_players(config.nba_season)

        details["stage"] = "validate"
        teams, team_rejected, duplicate_teams = normalize_teams(teams_raw)
        known_team_ids = {team.nba_team_id for team in teams}
        players, player_rejected, warnings, skipped_inactive, duplicate_players = (
            normalize_players(players_raw, known_team_ids)
        )
        rejected = team_rejected + player_rejected
        records_failed = len(rejected)

        details = _build_details(
            season=config.nba_season,
            stage="validate",
            teams_received=len(teams_raw),
            teams_persisted=0,
            teams_rejected=len(team_rejected),
            players_received=len(players_raw),
            players_persisted=0,
            players_rejected=len(player_rejected),
            players_warning=len(warnings),
            players_deactivation=0,
            skipped_inactive=skipped_inactive,
            rejected=rejected,
            warnings=warnings,
        )

        guard = evaluate_guards(
            teams=teams,
            players=players,
            duplicate_team_ids=duplicate_teams,
            duplicate_player_ids=duplicate_players,
            min_teams=config.min_teams,
            min_active_players=config.min_active_players,
        )
        details["guard"] = {"passed": guard.passed, "reason": guard.reason}
        if not guard.passed:
            details["stage"] = "deactivate_guard"
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
            season=config.nba_season,
            stage="persist",
            teams_received=len(teams_raw),
            teams_persisted=len(teams),
            teams_rejected=len(team_rejected),
            players_received=len(players_raw),
            players_persisted=len(players),
            players_rejected=len(player_rejected),
            players_warning=len(warnings),
            players_deactivation=0,
            skipped_inactive=skipped_inactive,
            rejected=rejected,
            warnings=warnings,
            guard=guard.passed,
            guard_reason=guard.reason,
        )
        records_processed = len(teams) + len(players)
        deactivated = data_store.persist_and_complete(
            run_id,
            teams=teams,
            players=players,
            records_processed=records_processed,
            records_failed=records_failed,
            details=persist_details,
        )
        persist_details["players"]["deactivation"] = deactivated
        persist_details["teams"]["deactivation"] = 0
        return ImportResult(
            success=True,
            run_id=run_id,
            status="COMPLETED",
            records_processed=records_processed,
            records_failed=records_failed,
            details=persist_details,
        )
    except Exception as exc:
        details["stage"] = details.get("stage") or "retrieve_teams"
        details.setdefault("guard", {"passed": False, "reason": None})
        return _fail(
            data_store,
            run_id,
            records_failed=int(details.get("teams", {}).get("rejected", 0))
            + int(details.get("players", {}).get("rejected", 0)),
            details=details,
            error_message=str(exc),
            secrets=secrets,
        )


def _fail(
    store: ImportStore,
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


def _empty_details(season: str) -> dict[str, Any]:
    return _build_details(
        season=season,
        stage="retrieve_teams",
        teams_received=0,
        teams_persisted=0,
        teams_rejected=0,
        players_received=0,
        players_persisted=0,
        players_rejected=0,
        players_warning=0,
        players_deactivation=0,
        skipped_inactive=0,
        rejected=[],
        warnings=[],
    )


def _build_details(
    *,
    season: str,
    stage: str,
    teams_received: int,
    teams_persisted: int,
    teams_rejected: int,
    players_received: int,
    players_persisted: int,
    players_rejected: int,
    players_warning: int,
    players_deactivation: int,
    skipped_inactive: int,
    rejected: list[RejectedRecord],
    warnings: list[WarningRecord],
    guard: bool | None = None,
    guard_reason: str | None = None,
) -> dict[str, Any]:
    return json_safe(
        {
            "season": season,
            "stage": stage,
            "teams": EntityCounts(
                received=teams_received,
                persisted=teams_persisted,
                rejected=teams_rejected,
                warning=0,
                deactivation=0,
            ).as_dict(),
            "players": {
                **EntityCounts(
                    received=players_received,
                    persisted=players_persisted,
                    rejected=players_rejected,
                    warning=players_warning,
                    deactivation=players_deactivation,
                ).as_dict(),
                "skipped_inactive": skipped_inactive,
            },
            "rejected": [
                {"reason": item.reason, "entity": item.entity, "raw": item.raw} for item in rejected
            ],
            "warnings": [
                {
                    "reason": item.reason,
                    "entity": item.entity,
                    "nba_player_id": item.nba_player_id,
                    "raw": item.raw,
                }
                for item in warnings
            ],
            "guard": {"passed": guard, "reason": guard_reason},
        }
    )
