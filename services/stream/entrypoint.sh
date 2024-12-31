#!/bin/bash

set -e

echo ">>> Stream Update <<<"
PYTHONUNBUFFERED=1 venv/bin/python stream-download.py
