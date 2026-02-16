#!/bin/bash

set -e
set -x

export PYTHONUNBUFFERED=1

echo $(date) ">>> Processing Stream Data <<<"

echo $(date) " Downloading new information from stream <<<"
venv/bin/python stream-download.py

echo $(date) " Downloading new information from dwr cymru <<<"
venv/bin/python dwr-cymru-download.py

echo $(date) " Persisting stream files to db <<<"
venv/bin/python stream-persist-content.py

echo $(date) " persist dwr cymru to db <<<"
venv/bin/python stream-persist-content-dwr-cymru.py

echo $(date) " Bodging CSOs that have gone away <<<"
venv/bin/python stream-bodge-disappeared.py

echo $(date) " Processing stream files into events <<<"
venv/bin/python stream-process-events.py

echo $(date) " Find constituencies for new CSOs <<<"
venv/bin/python grid-references-update.py

echo $(date) " Processing events <<<"
venv/bin/python stream-summarise-events.py

echo $(date) " >>> Complete <<<"

