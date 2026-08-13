"""JSON-safe helpers and secret redaction."""

from __future__ import annotations

from typing import Any


def redact_text(text: str, secrets: list[str | None]) -> str:
    """Replace known secrets in ``text``. Longer secrets are applied first."""
    redacted = text
    for secret in sorted((item for item in secrets if item), key=len, reverse=True):
        redacted = redacted.replace(secret, "***")
    return redacted


def json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [json_safe(item) for item in value]
    if isinstance(value, (str, bool)) or value is None:
        return value
    if isinstance(value, int) and not isinstance(value, bool):
        return int(value)
    if isinstance(value, float):
        return float(value)
    return str(value)
