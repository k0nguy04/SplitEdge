"""Command-line entry point for the SplitEdge batch importer."""

from __future__ import annotations

import sys
from collections.abc import Callable, Sequence

from splitedge_importer.config import Config, ConfigError, load_config, require_import_seasons
from splitedge_importer.games_pipeline import run_games_pipeline
from splitedge_importer.models import ImportResult
from splitedge_importer.pipeline import run_pipeline
from splitedge_importer.redact import redact_text
from splitedge_importer.retrieval.client import NbaSource

TEAMS_PLAYERS = "teams-players"
GAMES_STATS = "games-stats"


def main(
    argv: Sequence[str] | None = None,
    *,
    pipeline: Callable[..., ImportResult] | None = None,
    games_pipeline: Callable[..., ImportResult] | None = None,
    source: NbaSource | None = None,
    config_loader: Callable[[], Config] = load_config,
) -> int:
    """Run a terminating import command and return a process exit code."""
    command, error = _parse_command(sys.argv[1:] if argv is None else argv)
    if error is not None:
        print(error, file=sys.stderr)
        return 2

    try:
        config = config_loader()
        if command == GAMES_STATS:
            require_import_seasons(config)
    except ConfigError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    secrets = [config.database_url, config.password]
    if command == GAMES_STATS:
        injected = games_pipeline is not None
        run = games_pipeline or run_games_pipeline
    else:
        injected = pipeline is not None
        run = pipeline or run_pipeline
    try:
        live_source = source
        if live_source is None and not injected:
            from splitedge_importer.retrieval.nba_api_client import NbaApiClient

            live_source = NbaApiClient(
                timeout_seconds=config.http_timeout_seconds,
                retry_max_attempts=config.retry_max_attempts,
                retry_base_delay_seconds=config.retry_base_delay_seconds,
                retry_max_delay_seconds=config.retry_max_delay_seconds,
                request_interval_seconds=config.request_interval_seconds,
            )
        if live_source is None:
            result = run(config)
        else:
            result = run(config, source=live_source)
    except Exception as exc:
        print(redact_text(str(exc), secrets), file=sys.stderr)
        return 1

    if not result.success:
        message = result.error_message or "import failed"
        print(redact_text(message, secrets), file=sys.stderr)
        return 1

    print(_success_message(command, result))
    return 0


def _parse_command(argv: Sequence[str]) -> tuple[str, str | None]:
    if not argv:
        return TEAMS_PLAYERS, None
    command = argv[0]
    if command in {TEAMS_PLAYERS, GAMES_STATS}:
        return command, None
    return (
        command,
        "Unknown command. Use teams-players or games-stats.",
    )


def _success_message(command: str, result: ImportResult) -> str:
    if command == GAMES_STATS:
        return (
            "Import completed: "
            f"{result.details.get('games', {}).get('persisted', 0)} games, "
            f"{result.details.get('player_stats', {}).get('persisted', 0)} player stats "
            f"(run id={result.run_id})"
        )
    return (
        "Import completed: "
        f"{result.details.get('teams', {}).get('persisted', 0)} teams, "
        f"{result.details.get('players', {}).get('persisted', 0)} players "
        f"(run id={result.run_id})"
    )


if __name__ == "__main__":
    sys.exit(main())
