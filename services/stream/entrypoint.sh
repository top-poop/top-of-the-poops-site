#!/bin/bash

set -e
set -x

export PYTHONUNBUFFERED=1

echo $(date) ">>> Processing Stream Data <<<"

echo $(date) " Downloading new information from stream <<<"
venv/bin/python stream-download.py

echo $(date) " Persisting stream files to db <<<"
venv/bin/python stream-persist-content.py

echo $(date) " Processing stream files into events <<<"
venv/bin/python stream-process-events.py

echo $(date) " Downloading new information from dwr cymru <<<"
venv/bin/python dwr-cymru-download.py

echo $(date) " >>> Complete <<<"

