#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
RESET='\033[0m'

PID_DIR="/tmp"

echo ""
echo -e "${CYAN}================================================${RESET}"
echo -e "${CYAN}   TIKETI Spring - Stop All Services${RESET}"
echo -e "${CYAN}================================================${RESET}"
echo ""

# ── 1. Stop Spring Boot (Java) processes ─────────────────────────

echo -e "${CYAN}[1/2] Stopping Spring Boot services ...${RESET}"

JAVA_STOPPED=0
SERVICES=(auth-service ticket-service payment-service stats-service gateway-service)

for svc in "${SERVICES[@]}"; do
  PID_FILE="$PID_DIR/tiketi-$svc.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      kill "$PID" 2>/dev/null && JAVA_STOPPED=$((JAVA_STOPPED + 1)) || true
    fi
    rm -f "$PID_FILE"
  fi
done

# Fallback: kill by process pattern
if [ $JAVA_STOPPED -eq 0 ]; then
  pkill -f "tiketi" 2>/dev/null && JAVA_STOPPED=$((JAVA_STOPPED + 1)) || true
  pkill -f "bootRun" 2>/dev/null && JAVA_STOPPED=$((JAVA_STOPPED + 1)) || true
fi

if [ $JAVA_STOPPED -gt 0 ]; then
  echo -e "${GREEN}  Stopped Java/Gradle process(es)${RESET}"
else
  echo -e "${GRAY}  No running Spring services found${RESET}"
fi

# Stop Gradle daemons
for svc in "${SERVICES[@]}"; do
  GRADLEW="$REPO_ROOT/services-spring/$svc/gradlew"
  if [ -f "$GRADLEW" ]; then
    "$GRADLEW" -p "$REPO_ROOT/services-spring/$svc" --stop 2>/dev/null || true
  fi
done
echo -e "${GREEN}  Gradle daemons stopped${RESET}"

# ── 2. Stop Docker Compose (DB + Redis) ──────────────────────────

echo ""
echo -e "${CYAN}[2/2] Stopping databases and Redis ...${RESET}"

COMPOSE_FILE="$REPO_ROOT/services-spring/docker-compose.databases.yml"
if [ -f "$COMPOSE_FILE" ]; then
  if docker compose -f "$COMPOSE_FILE" down; then
    echo -e "${GREEN}  OK: DB containers stopped${RESET}"
  else
    echo -e "${YELLOW}  WARN: docker compose down returned error${RESET}"
  fi
else
  echo -e "${GRAY}  docker-compose.databases.yml not found (skipping)${RESET}"
fi

echo ""
echo -e "${GREEN}================================================${RESET}"
echo -e "${GREEN}   All services stopped.${RESET}"
echo -e "${GREEN}================================================${RESET}"
echo ""
