"""Parse NBA minute values into decimal minutes.

LeagueGameLog may return MM:SS strings, integers, or floats. DNP-style values
are skipped rather than stored as zero-minute performances. Values above 80
minutes are treated as malformed, not as a skip.
"""

from __future__ import annotations

from decimal import Decimal, InvalidOperation
from typing import Any

from splitedge_importer.validation.records import trim

SKIP_TOKENS = frozenset({"", "DNP", "DND", "NWT", "-", "NONE", "NULL"})
MAX_MINUTES = Decimal("80")


def parse_minutes(value: Any) -> tuple[Decimal | None, str | None]:
    """Return (minutes, None) on success, (None, reason) otherwise.

    Reasons:
    - skipped_dnp: missing, DNP-style, or non-positive minutes
    - malformed_minutes: unparsable or greater than 80
    """
    if value is None:
        return None, "skipped_dnp"
    if isinstance(value, bool):
        return None, "malformed_minutes"
    if isinstance(value, (int, float, Decimal)):
        return _from_number(Decimal(str(value)))
    text = trim(value).upper()
    if text in SKIP_TOKENS:
        return None, "skipped_dnp"
    if ":" in text:
        return _from_clock(text)
    try:
        return _from_number(Decimal(text))
    except InvalidOperation:
        return None, "malformed_minutes"


def _from_clock(text: str) -> tuple[Decimal | None, str | None]:
    parts = text.split(":")
    if len(parts) != 2:
        return None, "malformed_minutes"
    try:
        minutes = int(parts[0])
        seconds = int(parts[1])
    except ValueError:
        return None, "malformed_minutes"
    if minutes < 0 or seconds < 0 or seconds >= 60:
        return None, "malformed_minutes"
    value = Decimal(minutes) + (Decimal(seconds) / Decimal(60))
    return _from_number(value)


def _from_number(value: Decimal) -> tuple[Decimal | None, str | None]:
    if value <= 0:
        return None, "skipped_dnp"
    if value > MAX_MINUTES:
        return None, "malformed_minutes"
    return value.quantize(Decimal("0.001")), None
