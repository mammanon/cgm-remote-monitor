#!/bin/sh
#
# Updates the Carb Tracker to the newest version and restarts it.
#
#   ./update.sh
#
# A plain restart is NOT enough: restarting (or rebooting the server) starts
# the same code again. This script fetches the new code and rebuilds, which is
# what actually puts a new version on the phone.
#
# Meals, photos and backups live in their own Docker volumes, so they are not
# touched by this.

set -e
cd "$(dirname "$0")"

echo "1/2  Fetching the newest version…"
git pull

echo
echo "2/2  Rebuilding and restarting (this takes a few minutes)…"
docker compose up -d --build

echo
echo "Done. On the phone, reload the page — the new version is live."
