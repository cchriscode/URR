#!/usr/bin/env bash
set -euo pipefail

# Usage: ./run.sh <scenario> [options]
# Scenarios: browse, booking, queue-rush, mixed
# Example: ./run.sh browse --env BASE_URL=http://localhost:30080

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:30080}"

scenario="${1:-browse}"
shift || true

case "$scenario" in
  browse)
    k6 run "$SCRIPT_DIR/scenarios/browse-events.js" --env BASE_URL="$BASE_URL" "$@"
    ;;
  booking)
    k6 run "$SCRIPT_DIR/scenarios/booking-flow.js" --env BASE_URL="$BASE_URL" "$@"
    ;;
  queue-rush)
    k6 run "$SCRIPT_DIR/scenarios/queue-rush.js" --env BASE_URL="$BASE_URL" "$@"
    ;;
  mixed)
    k6 run "$SCRIPT_DIR/scenarios/mixed-traffic.js" --env BASE_URL="$BASE_URL" "$@"
    ;;
  *)
    echo "Unknown scenario: $scenario"
    echo "Available: browse, booking, queue-rush, mixed"
    exit 1
    ;;
esac
