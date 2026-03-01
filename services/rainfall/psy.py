from typing import Callable, Iterable, Tuple
from typing import TypeVar

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

T = TypeVar('T')


def iter_row(cursor, size=10, f: Callable[[Tuple], T] = lambda t: t) -> Iterable[T]:
    while True:
        rows = cursor.fetchmany(size)
        if not rows:
            break
        for row in rows:
            yield f(row)


def select_many(connection, sql, params=None, f: Callable[[Tuple], T] = lambda t: t) -> Iterable[T]:
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        yield from iter_row(cursor, size=100, f=f)


def select(connection, sql, params=None):
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        return cursor.fetchall()


def select_one(connection, sql, params=None):
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        return cursor.fetchone()


def connect(db_host) -> ConnectionPool:
    return ConnectionPool(
        min_size=1,
        max_size=10,
        kwargs={
            "host": db_host,
            "dbname": "gis",
            "user": "docker",
            "password": "docker",
            "row_factory": dict_row
        },
    )