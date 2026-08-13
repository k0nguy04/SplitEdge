"""Shared parsing helpers for NBA source rows."""

from __future__ import annotations

from typing import Any


def trim(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def parse_id(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def parse_positive_id(value: Any) -> int | None:
    parsed = parse_id(value)
    if parsed is None or parsed <= 0:
        return None
    return parsed


def is_active_roster_status(value: Any) -> bool:
    if value is True:
        return True
    if value is False or value is None:
        return False
    return str(value).strip() in {"1", "1.0"}
