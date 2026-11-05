#!/bin/sh
# Put a backup file back on the TV, then choose Cinematic TV Settings › Restore backup on the TV.
# Usage: scripts/restore.sh backup.json [tv-address]
set -e
[ -f "$1" ] || { echo "Usage: $0 backup.json [tv-address]"; exit 1; }
TV="${2:-10.0.0.6:5555}"
DIR=/sdcard/Android/data/com.cinematictv.launcher/files
adb connect "$TV" >/dev/null
adb -s "$TV" shell mkdir -p "$DIR"
adb -s "$TV" push "$1" "$DIR/cinematic-tv-backup.json"
echo "Pushed. On the TV: Control Centre › Cinematic TV Settings › Restore backup."
