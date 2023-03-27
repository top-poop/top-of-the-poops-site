from typing import Callable, Iterable, Mapping, Any
from typing import TypeVar

T = TypeVar('T')


def iter_row(cursor, size=10, f: Callable[[Mapping[str, Any]], T] = lambda t: t) -> Iterable[T]:
    columns = [desc[0] for desc in cursor.description]
    while True:
        rows = cursor.fetchmany(size)
        if not rows:
            break
        for row in rows:
            yield f(dict(zip(columns, row)))


def select_many(connection, sql, params=None, f: Callable[[Mapping[str, Any]], T] = lambda t: t) -> Iterable[T]:
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
