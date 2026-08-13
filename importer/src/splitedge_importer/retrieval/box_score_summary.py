"""BoxScoreSummaryV3 GameSummary parsing, validation, and checkpoint resource names."""

from __future__ import annotations

from typing import Any

from splitedge_importer.validation.records import parse_positive_id, trim

SUMMARY_RESOURCE_PREFIX = "box_score_summary:"
SUMMARY_KEYS = ("gameId", "gameStatus", "gameStatusText", "homeTeamId", "awayTeamId")
GAME_SUMMARY_HEADERS = (
    "gameId",
    "gameCode",
    "gameStatus",
    "gameStatusText",
    "period",
    "gameClock",
    "gameTimeUTC",
    "gameEt",
    "awayTeamId",
    "homeTeamId",
    "duration",
    "attendance",
    "sellout",
)
REFETCH_SUMMARY_REASONS = frozenset({"empty_summary", "malformed_summary", "wrong_game_id"})


class SummaryParseError(ValueError):
    """BoxScoreSummaryV3 payload is empty or not a GameSummary object."""


class HomeAwayResolverError(RuntimeError):
    """A completed ambiguous game could not be assigned designated home/away."""

    def __init__(self, reason: str, *, season: str, nba_game_id: str) -> None:
        super().__init__(f"{reason} ({season} {nba_game_id})")
        self.reason = reason
        self.season = season
        self.nba_game_id = nba_game_id


def summary_resource(nba_game_id: str) -> str:
    resource = f"{SUMMARY_RESOURCE_PREFIX}{nba_game_id}"
    if len(resource) > 32:
        raise ValueError(f"checkpoint resource exceeds VARCHAR(32): {resource}")
    return resource


def is_summary_resource(resource: str) -> bool:
    return resource.startswith(SUMMARY_RESOURCE_PREFIX)


def is_numeric_final(value: Any) -> bool:
    if isinstance(value, bool) or value is None:
        return False
    if isinstance(value, int):
        return value == 3
    text = trim(value)
    if text.isdigit():
        return int(text) == 3
    return False


def is_textual_final(value: Any) -> bool:
    folded = trim(value).casefold()
    return folded == "final" or folded.startswith("final/")


def is_final_status(game_status: Any, game_status_text: Any) -> bool:
    return is_numeric_final(game_status) and is_textual_final(game_status_text)


def extract_game_summary(payload: Any) -> dict[str, Any]:
    """Return the five GameSummary fields from a raw or parsed payload."""
    if not isinstance(payload, dict) or not payload:
        raise SummaryParseError("empty_summary")
    if "boxScoreSummary" in payload:
        summary = payload.get("boxScoreSummary")
        if not isinstance(summary, dict) or not summary:
            raise SummaryParseError("malformed_summary")
        return {
            "gameId": summary.get("gameId"),
            "gameStatus": summary.get("gameStatus"),
            "gameStatusText": summary.get("gameStatusText"),
            "homeTeamId": summary.get("homeTeamId"),
            "awayTeamId": summary.get("awayTeamId"),
        }
    nested = payload.get("GameSummary")
    if isinstance(nested, dict) and nested is not payload:
        return extract_game_summary(nested)
    if "headers" in payload and "data" in payload:
        headers = payload.get("headers")
        data = payload.get("data")
        if not isinstance(headers, list) or not isinstance(data, list) or not data:
            raise SummaryParseError("malformed_summary")
        if not isinstance(data[0], list):
            raise SummaryParseError("malformed_summary")
        row = dict(zip(headers, data[0], strict=False))
        return {key: row.get(key) for key in SUMMARY_KEYS}
    if all(key in payload for key in SUMMARY_KEYS):
        return {key: payload.get(key) for key in SUMMARY_KEYS}
    raise SummaryParseError("malformed_summary")


def validate_game_summary(
    payload: Any,
    *,
    requested_game_id: str,
    log_team_ids: set[int],
    season: str,
) -> dict[str, Any]:
    try:
        extracted = extract_game_summary(payload)
    except SummaryParseError as exc:
        reason = str(exc) if str(exc) in {"empty_summary", "malformed_summary"} else (
            "malformed_summary"
        )
        raise HomeAwayResolverError(
            reason, season=season, nba_game_id=requested_game_id
        ) from exc

    missing = [key for key in SUMMARY_KEYS if extracted.get(key) in (None, "")]
    if missing:
        raise HomeAwayResolverError(
            "malformed_summary", season=season, nba_game_id=requested_game_id
        )

    game_id = _normalize_game_id(extracted["gameId"])
    if game_id != requested_game_id:
        raise HomeAwayResolverError("wrong_game_id", season=season, nba_game_id=requested_game_id)
    if not is_final_status(extracted["gameStatus"], extracted["gameStatusText"]):
        raise HomeAwayResolverError(
            "non_final_status", season=season, nba_game_id=requested_game_id
        )

    home_id = parse_positive_id(extracted["homeTeamId"])
    away_id = parse_positive_id(extracted["awayTeamId"])
    if home_id is None or away_id is None:
        raise HomeAwayResolverError(
            "invalid_team_ids", season=season, nba_game_id=requested_game_id
        )
    if home_id == away_id:
        raise HomeAwayResolverError(
            "invalid_team_ids", season=season, nba_game_id=requested_game_id
        )
    if {home_id, away_id} != log_team_ids:
        raise HomeAwayResolverError("team_mismatch", season=season, nba_game_id=requested_game_id)

    return {
        "gameId": game_id,
        "gameStatus": extracted["gameStatus"],
        "gameStatusText": extracted["gameStatusText"],
        "homeTeamId": home_id,
        "awayTeamId": away_id,
    }


def _normalize_game_id(value: Any) -> str | None:
    raw = trim(value)
    if not raw:
        return None
    if raw.isdigit():
        raw = raw.zfill(10)
    return raw
