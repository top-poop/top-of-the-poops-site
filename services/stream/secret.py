import os
from typing import Optional


def secret_value(name) -> Optional[str]:
    path = f"/run/secrets/{name}"
    if os.path.exists(path):
        with open(path) as f:
            return f.readline().strip()
    return None


def env(name: str, secret_name: str) -> str:
    value = os.environ.get(name)
    if value is None:
        value = secret_value(secret_name)
    if value is None:
        raise IOError(f"Can't get env:{name} or secret:{secret_name}")
    return value
