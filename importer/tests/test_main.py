from splitedge_importer.config import ConfigError, load_config
from splitedge_importer.main import main
from splitedge_importer.models import ImportResult


def _success_result() -> ImportResult:
    return ImportResult(
        success=True,
        run_id=7,
        status="COMPLETED",
        records_processed=5,
        records_failed=0,
        details={"teams": {"persisted": 3}, "players": {"persisted": 2}},
    )


def test_cli_success(monkeypatch) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    assert main([], pipeline=lambda config: _success_result()) == 0


def test_cli_pipeline_failure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")

    def fail(config: object) -> ImportResult:
        del config
        return ImportResult(success=False, error_message="batch failed")

    assert main([], pipeline=fail) == 1
    captured = capsys.readouterr()
    assert "batch failed" in captured.err


def test_cli_pipeline_exception(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")

    def boom(config: object) -> ImportResult:
        del config
        raise RuntimeError("persist exploded")

    assert main([], pipeline=boom) == 1
    assert "persist exploded" in capsys.readouterr().err


def test_cli_missing_nba_season(monkeypatch, capsys) -> None:
    monkeypatch.delenv("NBA_SEASON", raising=False)
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    called = {"pipeline": False}

    def pipeline(config: object) -> ImportResult:
        del config
        called["pipeline"] = True
        return _success_result()

    assert main([], pipeline=pipeline) == 2
    assert called["pipeline"] is False
    assert "NBA_SEASON" in capsys.readouterr().err


def test_cli_missing_database_url(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert main([]) == 2
    assert "DATABASE_URL" in capsys.readouterr().err


def test_cli_malformed_season(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    assert main([]) == 2
    assert "YYYY-YY" in capsys.readouterr().err


def test_cli_secret_redaction_omits_password(monkeypatch, capsys) -> None:
    url = "postgresql://splitedge:s3cretpass@localhost:5432/splitedge"
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", url)

    def boom(config: object) -> ImportResult:
        raise RuntimeError(f"could not connect to {config.database_url}")  # type: ignore[attr-defined]

    assert main([], pipeline=boom) == 1
    captured = capsys.readouterr()
    combined = captured.out + captured.err
    assert "s3cretpass" not in combined
    assert url not in combined
    assert "could not connect to" in captured.err


def test_load_config_does_not_derive_season(monkeypatch) -> None:
    monkeypatch.delenv("NBA_SEASON", raising=False)
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    try:
        load_config()
    except ConfigError as exc:
        assert "NBA_SEASON" in str(exc)
    else:
        raise AssertionError("expected ConfigError")


def test_cli_games_stats_success(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    monkeypatch.setenv("NBA_IMPORT_SEASONS", "2023-24,2024-25,2025-26")

    def pipeline(config: object, **kwargs: object) -> ImportResult:
        del config, kwargs
        return ImportResult(
            success=True,
            run_id=9,
            status="COMPLETED",
            details={"games": {"persisted": 4}, "player_stats": {"persisted": 10}},
        )

    assert main(["games-stats"], games_pipeline=pipeline) == 0
    assert "4 games" in capsys.readouterr().out


def test_cli_games_stats_requires_import_seasons(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    monkeypatch.delenv("NBA_IMPORT_SEASONS", raising=False)
    called = {"pipeline": False}

    def pipeline(config: object, **kwargs: object) -> ImportResult:
        del config, kwargs
        called["pipeline"] = True
        return ImportResult(success=True)

    assert main(["games-stats"], games_pipeline=pipeline) == 2
    assert called["pipeline"] is False
    assert "NBA_IMPORT_SEASONS" in capsys.readouterr().err


def test_cli_unknown_command(monkeypatch, capsys) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    assert main(["reports"]) == 2
    assert "games-stats" in capsys.readouterr().err


def test_load_config_parses_import_seasons(monkeypatch) -> None:
    monkeypatch.setenv("NBA_SEASON", "2025-26")
    monkeypatch.setenv("DATABASE_URL", "postgresql://splitedge:s3cretpass@localhost:5432/splitedge")
    monkeypatch.setenv("NBA_IMPORT_SEASONS", "2023-24, 2024-25,2025-26,2023-24")
    config = load_config()
    assert config.import_seasons == ("2023-24", "2024-25", "2025-26")
