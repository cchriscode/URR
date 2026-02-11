#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
RESET='\033[0m'

FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
  esac
done

PID_DIR="/tmp"

echo ""
echo -e "${RED}==========================================${RESET}"
echo -e "${RED}   TIKETI Spring - Complete Cleanup${RESET}"
echo -e "${RED}==========================================${RESET}"
echo ""
echo -e "${YELLOW}This will DELETE:${RESET}"
echo -e "${WHITE}  - Running Spring Boot (Java) processes${RESET}"
echo -e "${WHITE}  - Docker Compose DB containers + data${RESET}"
echo -e "${WHITE}  - Kind cluster 'tiketi-local'${RESET}"
echo -e "${WHITE}  - Port-forward processes${RESET}"
echo -e "${WHITE}  - Docker images (optional)${RESET}"
echo -e "${WHITE}  - node_modules (optional)${RESET}"
echo -e "${WHITE}  - Gradle caches (optional)${RESET}"
echo ""

if ! $FORCE; then
  read -rp "Are you sure? (y/N) " CONFIRM
  if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo -e "${YELLOW}Aborted.${RESET}"
    exit 0
  fi
fi

echo ""

SERVICES=(auth-service ticket-service payment-service stats-service gateway-service)

# ── 1. Stop Spring Boot (Java) processes ─────────────────────────

echo -e "${CYAN}[1/6] Stopping Spring Boot services ...${RESET}"

JAVA_STOPPED=0
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

pkill -f "tiketi"  2>/dev/null && JAVA_STOPPED=$((JAVA_STOPPED + 1)) || true
pkill -f "bootRun" 2>/dev/null && JAVA_STOPPED=$((JAVA_STOPPED + 1)) || true

if [ $JAVA_STOPPED -gt 0 ]; then
  echo -e "${GREEN}  Stopped Java/Gradle process(es)${RESET}"
else
  echo -e "${GRAY}  No Spring processes found${RESET}"
fi

# Stop Gradle daemons
for svc in "${SERVICES[@]}"; do
  GRADLEW="$REPO_ROOT/services-spring/$svc/gradlew"
  if [ -f "$GRADLEW" ]; then
    "$GRADLEW" -p "$REPO_ROOT/services-spring/$svc" --stop 2>/dev/null || true
  fi
done
echo -e "${GREEN}  Gradle daemons stopped${RESET}"

# ── 2. Stop port-forward processes ───────────────────────────────

echo ""
echo -e "${CYAN}[2/6] Stopping port-forward processes ...${RESET}"

pkill -f "kubectl port-forward" 2>/dev/null || true
for pid_file in "$PID_DIR"/tiketi-pf-*.pid; do
  [ -f "$pid_file" ] && rm -f "$pid_file" || true
done

echo -e "${GREEN}  Port-forwards stopped${RESET}"
sleep 1

# ── 3. Stop Docker Compose containers ────────────────────────────

echo ""
echo -e "${CYAN}[3/6] Stopping Docker Compose containers ...${RESET}"

COMPOSE_FILE="$REPO_ROOT/services-spring/docker-compose.databases.yml"
if [ -f "$COMPOSE_FILE" ]; then
  if docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null; then
    echo -e "${GREEN}  Containers + volumes removed${RESET}"
  else
    echo -e "${GRAY}  No containers running or Docker not available${RESET}"
  fi
else
  echo -e "${GRAY}  docker-compose file not found (skipping)${RESET}"
fi

# ── 4. Delete Kind cluster ────────────────────────────────────────

echo ""
echo -e "${CYAN}[4/6] Deleting Kind cluster ...${RESET}"

if command -v kind >/dev/null 2>&1; then
  if kind get clusters 2>/dev/null | grep -qx "tiketi-local"; then
    if kind delete cluster --name tiketi-local; then
      echo -e "${GREEN}  Cluster 'tiketi-local' deleted${RESET}"
    else
      echo -e "${YELLOW}  Failed to delete cluster${RESET}"
    fi
  else
    echo -e "${GRAY}  Cluster 'tiketi-local' not found${RESET}"
  fi
else
  echo -e "${GRAY}  kind not installed (skipping)${RESET}"
fi

# ── 5. Docker images (optional) ──────────────────────────────────

echo ""
if ! $FORCE; then
  read -rp "Delete Docker images? (y/N) " DELETE_IMAGES
else
  DELETE_IMAGES="y"
fi

if [[ "$DELETE_IMAGES" == "y" || "$DELETE_IMAGES" == "Y" ]]; then
  echo -e "${CYAN}[5/6] Deleting Docker images ...${RESET}"

  IMAGES=(
    "tiketi-spring-gateway-service:local"
    "tiketi-spring-auth-service:local"
    "tiketi-spring-ticket-service:local"
    "tiketi-spring-payment-service:local"
    "tiketi-spring-stats-service:local"
    "tiketi-spring-frontend:local"
  )

  for image in "${IMAGES[@]}"; do
    if docker images -q "$image" 2>/dev/null | grep -q .; then
      docker rmi "$image" 2>/dev/null && echo -e "${GREEN}  Deleted: $image${RESET}" || true
    fi
  done
else
  echo -e "${GRAY}[5/6] Docker images kept${RESET}"
fi

# ── 6. node_modules + Gradle caches (optional) ───────────────────

echo ""
if ! $FORCE; then
  read -rp "Delete node_modules and Gradle build caches? (y/N) " DELETE_CACHES
else
  DELETE_CACHES="y"
fi

if [[ "$DELETE_CACHES" == "y" || "$DELETE_CACHES" == "Y" ]]; then
  echo -e "${CYAN}[6/6] Deleting caches ...${RESET}"

  WEB_NODE_MODULES="$REPO_ROOT/apps/web/node_modules"
  if [ -d "$WEB_NODE_MODULES" ]; then
    echo -e "${GRAY}  Deleting apps/web/node_modules ...${RESET}"
    rm -rf "$WEB_NODE_MODULES" && echo -e "${GREEN}  Deleted: apps/web/node_modules${RESET}" || \
      echo -e "${YELLOW}  Failed to delete node_modules${RESET}"
  fi

  for svc in "${SERVICES[@]}"; do
    BUILD_DIR="$REPO_ROOT/services-spring/$svc/build"
    if [ -d "$BUILD_DIR" ]; then
      rm -rf "$BUILD_DIR" && echo -e "${GREEN}  Deleted: services-spring/$svc/build${RESET}" || \
        echo -e "${YELLOW}  Failed to delete $svc build dir${RESET}"
    fi
  done
else
  echo -e "${GRAY}[6/6] Caches kept${RESET}"
fi

# ── Summary ───────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}==========================================${RESET}"
echo -e "${GREEN}   Cleanup Complete!${RESET}"
echo -e "${GREEN}==========================================${RESET}"
echo ""
echo -e "${WHITE}  To start again:${RESET}"
echo -e "${CYAN}    ./scripts/start-all.sh --build --with-frontend${RESET}"
echo ""
