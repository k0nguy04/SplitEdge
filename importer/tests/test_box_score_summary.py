from importlib.metadata import version
from pathlib import Path

import pytest
from conftest import load_fixture

from splitedge_importer.retrieval.box_score_summary import (
    GAME_SUMMARY_HEADERS,
    HomeAwayResolverError,
    extract_game_summary,
    is_final_status,
    summary_resource,
    validate_game_summary,
)

GSW = 1610612744
BOS = 1610612738
LOG_TEAMS = {GSW, BOS}
SEASON = "2024-25"
GAME_ID = "0022400999"


def _valid() -> dict:
    return dict(load_fixture("box_score_summary_neutral.json"))


def test_summary_resource_fits_varchar32() -> None:
    resource = summary_resource(GAME_ID)
    assert resource == "box_score_summary:0022400999"
    assert len(resource) <= 32


@pytest.mark.parametrize(
    ("status", "text", "expected"),
    [
        (3, "Final", True),
        ("3", "Final", True),
        (3, "FINAL", True),
        (3, "Final/OT", True),
        (3, "final/ot", True),
        (2, "Final", False),
        (3, "In Progress", False),
        (3, "Scheduled", False),
        (3, "Finally", False),
        (3, "", False),
        (3, None, False),
        (None, "Final", False),
        (True, "Final", False),
        (3.0, "Final", False),
        ("03", "Final", True),
    ],
)
def test_final_status_requires_numeric_and_textual_indicators(
    status: object, text: object, expected: bool
) -> None:
    assert is_final_status(status, text) is expected


def test_contradictory_status_is_not_final() -> None:
    assert is_final_status(3, "In Progress") is False
    assert is_final_status(2, "Final") is False
    assert is_final_status(3, "Final") is True


def test_extracts_raw_box_score_summary_keys() -> None:
    extracted = extract_game_summary(load_fixture("box_score_summary_raw.json"))
    assert extracted["gameId"] == GAME_ID
    assert extracted["gameStatus"] == 3
    assert extracted["gameStatusText"] == "Final"
    assert extracted["homeTeamId"] == GSW
    assert extracted["awayTeamId"] == BOS


def test_extracts_parsed_game_summary_headers_and_data() -> None:
    raw = load_fixture("box_score_summary_raw.json")["boxScoreSummary"]
    row = [raw.get(header) for header in GAME_SUMMARY_HEADERS]
    payload = {"headers": list(GAME_SUMMARY_HEADERS), "data": [row]}
    extracted = extract_game_summary(payload)
    assert extracted["gameId"] == GAME_ID
    assert extracted["homeTeamId"] == GSW
    assert extracted["awayTeamId"] == BOS


def test_validate_accepts_integer_or_numeric_string_status() -> None:
    payload = _valid()
    payload["gameStatus"] = "3"
    validated = validate_game_summary(
        payload, requested_game_id=GAME_ID, log_team_ids=LOG_TEAMS, season=SEASON
    )
    assert validated["homeTeamId"] == GSW
    assert validated["awayTeamId"] == BOS


@pytest.mark.parametrize(
    ("mutate", "reason"),
    [
        (lambda item: {}, "empty_summary"),
        (lambda item: {"foo": 1}, "malformed_summary"),
        (lambda item: {**item, "gameId": "0022400001"}, "wrong_game_id"),
        (lambda item: {**item, "gameStatus": 2}, "non_final_status"),
        (lambda item: {**item, "gameStatusText": "In Progress"}, "non_final_status"),
        (lambda item: {**item, "gameStatus": 3, "gameStatusText": "Final"}, None),
        (lambda item: {**item, "homeTeamId": None}, "malformed_summary"),
        (lambda item: {**item, "homeTeamId": 0}, "invalid_team_ids"),
        (lambda item: {**item, "awayTeamId": -1}, "invalid_team_ids"),
        (lambda item: {**item, "homeTeamId": GSW, "awayTeamId": GSW}, "invalid_team_ids"),
        (lambda item: {**item, "awayTeamId": 1610612747}, "team_mismatch"),
    ],
)
def test_validate_game_summary_failure_categories(mutate, reason: str | None) -> None:
    payload = mutate(_valid())
    if reason is None:
        validate_game_summary(
            payload, requested_game_id=GAME_ID, log_team_ids=LOG_TEAMS, season=SEASON
        )
        return
    with pytest.raises(HomeAwayResolverError) as exc:
        validate_game_summary(
            payload, requested_game_id=GAME_ID, log_team_ids=LOG_TEAMS, season=SEASON
        )
    assert exc.value.reason == reason
    assert exc.value.nba_game_id == GAME_ID


def test_pyproject_requires_nba_api_1_11() -> None:
    text = (
        Path(__file__).resolve().parents[1].joinpath("pyproject.toml").read_text(encoding="utf-8")
    )
    assert "nba-api>=1.11.0,<2.0" in text


def test_nba_api_box_score_summary_v3_game_summary_shape() -> None:
    from nba_api.stats.endpoints import boxscoresummaryv3
    from nba_api.stats.endpoints._expected_data.boxscoresummaryv3 import _EXPECTED_DATA
    from nba_api.stats.endpoints._parsers.boxscoresummaryv3 import (
        NBAStatsBoxscoreSummaryParserV3,
    )

    assert version("nba-api").startswith("1.11")
    endpoint = boxscoresummaryv3.BoxScoreSummaryV3(game_id=GAME_ID, get_request=False)
    assert endpoint.endpoint == "boxscoresummaryv3"
    parser = NBAStatsBoxscoreSummaryParserV3(load_fixture("box_score_summary_raw.json"))
    headers = parser.get_game_summary_headers()
    assert headers == list(_EXPECTED_DATA["GameSummary"])
    assert headers == list(GAME_SUMMARY_HEADERS)
    row = dict(zip(headers, parser.get_game_summary_data()[0], strict=True))
    assert row["gameId"] == GAME_ID
    assert row["gameStatus"] == 3
    assert row["gameStatusText"] == "Final"
    assert row["homeTeamId"] == GSW
    assert row["awayTeamId"] == BOS
    assert "data" in {"headers": headers, "data": parser.get_game_summary_data()}
