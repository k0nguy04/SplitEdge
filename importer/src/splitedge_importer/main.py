"""Command-line entry point for the SplitEdge batch importer."""

from __future__ import annotations

import sys
from collections.abc import Callable, Sequence

from splitedge_importer.config import Config, ConfigError, load_config
from splitedge_importer.models import ImportResult
from splitedge_importer.pipeline import run_pipeline
from splitedge_importer.redact import redact_text
from splitedge_importer.retrieval.client import NbaSource


def main(
    argv: Sequence[str] | None = None,
    *,
    pipeline: Callable[..., ImportResult] | None = None,
    source: NbaSource | None = None,
    config_loader: Callable[[], Config] = load_config,
) -> int:
    """Run the teams-and-active-players import and return a process exit code."""
    del argv
    try:
        config = config_loader()
    except ConfigError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    secrets = [config.database_url, config.password]
    run = pipeline or run_pipeline
    try:
        live_source = source
        if live_source is None and pipeline is None:
            from splitedge_importer.retrieval.nba_api_client import NbaApiClient

            live_source = NbaApiClient(
                timeout_seconds=config.http_timeout_seconds,
                retry_max_attempts=config.retry_max_attempts,
                retry_base_delay_seconds=config.retry_base_delay_seconds,
                retry_max_delay_seconds=config.retry_max_delay_seconds,
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

    print(
        "Import completed: "
        f"{result.details.get('teams', {}).get('persisted', 0)} teams, "
        f"{result.details.get('players', {}).get('persisted', 0)} players "
        f"(run id={result.run_id})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
