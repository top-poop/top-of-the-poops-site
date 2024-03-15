#!/bin/bash

set -e

echo ">>> Thames Update <<<"

echo "Updating Thames Alerts"
venv/bin/python thames-populate.py --update
echo "Processing Thames Status"
venv/bin/python thames-process.py