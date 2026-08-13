"""Database connection helpers. This module never applies schema migrations."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from urllib.parse import unquote, urlparse

import psycopg


def connect(database_url: str) -> psycopg.Connection:
    return psycopg.connect(database_url, autocommit=False)


@contextmanager
def connection(database_url: str) -> Iterator[psycopg.Connection]:
    conn = connect(database_url)
    try:
        yield conn
    finally:
        conn.close()


def jdbc_url(database_url: str) -> str:
    parsed = urlparse(database_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or 5432
    database = (parsed.path or "/splitedge").lstrip("/")
    return f"jdbc:postgresql://{host}:{port}/{database}"


def url_user(database_url: str) -> str | None:
    parsed = urlparse(database_url)
    return unquote(parsed.username) if parsed.username else None


def url_password(database_url: str) -> str | None:
    parsed = urlparse(database_url)
    return unquote(parsed.password) if parsed.password else None
