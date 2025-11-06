
import contextlib
import re
import sys
import os
import unicodedata


def kebabcase(s):
    nfkd_form = unicodedata.normalize('NFD', s.lower())
    s = ''.join(c for c in nfkd_form if not unicodedata.combining(c))

    return "-".join(re.findall(
        r"[A-Z]{2,}(?=[A-Z][a-z]+[0-9]*|\b)|[A-Z]?[a-z]+[0-9]*|[A-Z]|[0-9]+",
       s
    ))


def test_kebabcase():
    assert kebabcase("bob") == "bob"
    assert kebabcase("bob smith") == "bob-smith"
    assert kebabcase("Bob Smith") == "bob-smith"
    assert kebabcase("Bob,Smith") == "bob-smith"
    assert kebabcase("Ynys Môn") == "ynys-mon"
    assert kebabcase("Aberdâr") == "aberdar"
    assert kebabcase("An t-Àrchar") == "an-t-archar"
    assert kebabcase("Lìonal") == "lional"


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