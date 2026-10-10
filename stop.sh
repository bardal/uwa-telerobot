#!/bin/sh
# Stop everything start.sh launched.
pids="$(cd "$(dirname "$0")" && pwd)/run/pids.txt"
[ -f "$pids" ] || { echo "nothing to stop"; exit 0; }
while read -r id name; do
  kill "$id" 2>/dev/null && echo "stopped $name ($id)" || echo "$name ($id) already gone"
done < "$pids"
rm "$pids"
