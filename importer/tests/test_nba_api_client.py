from types import SimpleNamespace

from splitedge_importer.retrieval.nba_api_client import (
    MalformedNbaPayload,
    _game_summary_from_payload,
    _reraise_http_error,
    _rows_from_league_game_log,
    _rows_from_nba_dict,
)
from splitedge_importer.retrieval.retry import NonRetryableHttpError, RetryableError


def test_rows_from_result_sets() -> None:
    payload = {
        "resultSets": [
            {
                "headers": ["PERSON_ID", "DISPLAY_FIRST_LAST"],
                "rowSet": [[201939, "Stephen Curry"]],
            }
        ]
    }
    rows = _rows_from_nba_dict(payload)
    assert rows == [{"PERSON_ID": 201939, "DISPLAY_FIRST_LAST": "Stephen Curry"}]


def test_reraise_429_is_retryable() -> None:
    exc = Exception("too many requests")
    exc.response = SimpleNamespace(status_code=429, headers={"Retry-After": "2"})  # type: ignore[attr-defined]
    try:
        _reraise_http_error(exc)
    except RetryableError as retryable:
        assert retryable.retry_after == "2"
    else:
        raise AssertionError("expected RetryableError")


def test_reraise_404_is_not_retryable() -> None:
    response = SimpleNamespace(status_code=404, headers={})
    exc = Exception("not found")
    exc.response = response  # type: ignore[attr-defined]
    try:
        _reraise_http_error(exc)
    except NonRetryableHttpError as err:
        assert err.status_code == 404
    else:
        raise AssertionError("expected NonRetryableHttpError")


def test_league_game_log_requires_rows_and_columns() -> None:
    headers = [
        "GAME_ID",
        "TEAM_ID",
        "TEAM_ABBREVIATION",
        "GAME_DATE",
        "MATCHUP",
        "WL",
        "PTS",
    ]
    row = ["0022300001", 1, "GSW", "2023-10-24", "GSW vs. BOS", "W", 100]
    payload = {"resultSets": [{"headers": headers, "rowSet": [row]}]}
    rows = _rows_from_league_game_log(payload, resource="team_game_log")
    assert rows[0]["GAME_ID"] == "0022300001"

    try:
        _rows_from_league_game_log(
            {"resultSets": [{"headers": headers, "rowSet": []}]},
            resource="team_game_log",
        )
    except MalformedNbaPayload as exc:
        assert "empty" in str(exc)
    else:
        raise AssertionError("expected MalformedNbaPayload")

    try:
        _rows_from_league_game_log(
            {"resultSets": [{"headers": ["GAME_ID"], "rowSet": [["0022300001"]]}]},
            resource="player_game_log",
        )
    except MalformedNbaPayload:
        pass
    else:
        raise AssertionError("expected MalformedNbaPayload")


def test_game_summary_from_raw_box_score_summary() -> None:
    from conftest import load_fixture

    extracted = _game_summary_from_payload(load_fixture("box_score_summary_raw.json"))
    assert extracted["gameId"] == "0022400999"
    assert extracted["homeTeamId"] == 1610612744
    assert extracted["awayTeamId"] == 1610612738


def test_game_summary_from_parsed_headers_and_data() -> None:
    from splitedge_importer.retrieval.box_score_summary import GAME_SUMMARY_HEADERS

    raw = {
        "gameId": "0022400999",
        "gameCode": "20241102/BOSGSW",
        "gameStatus": 3,
        "gameStatusText": "Final",
        "period": 4,
        "gameClock": "",
        "gameTimeUTC": "",
        "gameEt": "",
        "awayTeamId": 1610612738,
        "homeTeamId": 1610612744,
        "duration": 130,
        "attendance": 0,
        "sellout": "0",
    }
    payload = {
        "headers": list(GAME_SUMMARY_HEADERS),
        "data": [[raw[key] for key in GAME_SUMMARY_HEADERS]],
    }
    extracted = _game_summary_from_payload(payload)
    assert extracted["gameId"] == "0022400999"
    assert extracted["gameStatus"] == 3


def test_empty_or_malformed_summary_is_not_retryable() -> None:
    try:
        _game_summary_from_payload({})
    except MalformedNbaPayload as exc:
        assert "empty_summary" in str(exc)
    else:
        raise AssertionError("expected MalformedNbaPayload")
    try:
        _game_summary_from_payload({"resultSets": []})
    except MalformedNbaPayload:
        pass
    else:
        raise AssertionError("expected MalformedNbaPayload")

