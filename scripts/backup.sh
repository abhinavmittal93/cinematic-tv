#!/bin/sh
# Copy Cinematic TV's Dock + settings backup from the TV to this folder.
# Usage: scripts/backup.sh [tv-address]   (default 10.0.0.6:5555)
set -e
TV="${1:-10.0.0.6:5555}"
REMOTE=/sdcard/Android/data/com.cinematictv.launcher/files/cinematic-tv-backup.json
OUT="cinematic-tv-backup-$(date +%Y%m%d-%H%M).json"
adb connect "$TV" >/dev/null
adb -s "$TV" pull "$REMOTE" "$OUT"
echo "Saved $OUT"
