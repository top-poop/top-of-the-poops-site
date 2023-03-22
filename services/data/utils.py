
import contextlib
import re
import sys
import os

def kebabcase(s):
    s = s.replace("ô", "o")
    return "-".join(re.findall(
        r"[A-Z]{2,}(?=[A-Z][a-z]+[0-9]*|\b)|[A-Z]?[a-z]+[0-9]*|[A-Z]|[0-9]+",
        s.lower()
    ))


def test_kebabcase():
    assert kebabcase("bob") == "bob"
    assert kebabcase("bob smith") == "bob-smith"
    assert kebabcase("Bob Smith") == "bob-smith"
    assert kebabcase("Bob,Smith") == "bob-smith"
    assert kebabcase("Ynys Môn") == "ynys-mon"


def iter_row(cursor, size=10):
    while True:
        rows = cursor.fetchmany(size)
        if not rows:
            break
        for row in rows:
            yield row


@contextlib.contextmanager
def smart_open(filename=None):
    is_file = False
    if filename and filename != '-':
        fh = open(filename, 'w')
        is_file = True
    else:
        fh = sys.stdout

    try:
        try:
            yield fh
        finally:
            if fh is not sys.stdout:
                fh.close()
    except:
        if is_file:
            os.unlink(filename)
        raise