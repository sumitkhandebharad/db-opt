#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3010}"
REQUESTS="${REQUESTS:-1000}"

if ! [[ "$REQUESTS" =~ ^[0-9]+$ ]] || (( REQUESTS < 1 || REQUESTS > 10000 )); then
  echo "REQUESTS must be an integer between 1 and 10000 (got: $REQUESTS)" >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to parse benchmark responses" >&2
  exit 2
fi

printf 'Sequential lookup benchmark\nURL: %s/benchmark\nLookups per variant: %s\n\n' "$BASE_URL" "$REQUESTS"
printf '%-10s %14s %16s %16s %16s %14s\n' \
  'Variant' 'Completed' 'Total (ms)' 'Avg/query (ms)' 'Max/query (ms)' 'Queries/sec'
printf '%s\n' '----------------------------------------------------------------------------------------'

for variant in v1 v2 v3 ; do
  if ! response=$(curl --fail --silent --show-error \
    --get "$BASE_URL/benchmark" \
    --data-urlencode "variant=$variant" \
    --data-urlencode "requests=$REQUESTS"); then
    echo "Benchmark failed for $variant. Check that the server is running and its database is populated." >&2
    exit 1
  fi

  if ! row=$(python3 -c '
import json, sys
try:
    result = json.load(sys.stdin)
    print("{variant:<10} {completed:>14} {total:>16.3f} {average:>16.6f} {maximum:>16.6f} {rate:>14.2f}".format(
        variant=result["variant"],
        completed=result["completed_queries"],
        total=result["total_time_ms"],
        average=result["average_query_time_ms"],
        maximum=result["max_query_time_ms"],
        rate=result["queries_per_second"],
    ))
except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
    print("Could not parse benchmark response: {}".format(error), file=sys.stderr)
    sys.exit(1)
' <<< "$response"); then
    echo "Benchmark returned an invalid response for $variant." >&2
    exit 1
  fi

  printf '%s\n' "$row"
done

printf '\nEach variant is measured sequentially in one server-side benchmark call; results are not a concurrent load test.\n'
