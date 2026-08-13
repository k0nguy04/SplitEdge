from decimal import Decimal

from splitedge_importer.normalization.minutes import parse_minutes


def test_parses_clock_to_decimal_minutes() -> None:
    minutes, reason = parse_minutes("36:30")
    assert reason is None
    assert minutes == Decimal("36.500")


def test_parses_numeric_minutes() -> None:
    minutes, reason = parse_minutes(32.25)
    assert reason is None
    assert minutes == Decimal("32.250")


def test_skips_dnp_and_empty() -> None:
    for value in (None, "", "DNP", "DND", "NWT", "-", "0:00", 0):
        minutes, reason = parse_minutes(value)
        assert minutes is None
        assert reason == "skipped_dnp"


def test_rejects_minutes_above_80() -> None:
    minutes, reason = parse_minutes("99:00")
    assert minutes is None
    assert reason == "malformed_minutes"


def test_accepts_exactly_80_minutes() -> None:
    minutes, reason = parse_minutes("80:00")
    assert reason is None
    assert minutes == Decimal("80.000")


def test_rejects_invalid_seconds() -> None:
    minutes, reason = parse_minutes("12:60")
    assert minutes is None
    assert reason == "malformed_minutes"
