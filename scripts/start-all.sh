#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
RESET='\033[0m'

BUILD=false
WITH_FRONTEND=false

for arg in "$@"; do
  case "$arg" in
    --build)         BUILD=true ;;
    --with-frontend) WITH_FRONTEND=true ;;
  esac
done

echo ""
echo -e "${CYAN}================================================${RESET}"
echo -e "${CYAN}   TIKETI Spring - Start All Services${RESET}"
echo -e "${CYAN}================================================${RESET}"
echo ""

# ── 0. Environment Variables (Local Dev Secrets) ─────────────────

echo -e "${CYAN}[0/3] Setting environment variables ...${RESET}"

export JWT_SECRET="local-dev-jwt-secret-minimum-32-characters-long"
export INTERNAL_API_TOKEN="local-dev-internal-api-token"
export QUEUE_ENTRY_TOKEN_SECRET="local-dev-queue-entry-token-secret-min-32-chars"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
export ZIPKIN_ENDPOINT="http://localhost:9411/api/v2/spans"
export TRACING_SAMPLING_PROBABILITY="1.0"
export TOSS_CLIENT_KEY="test_ck_dummy"
export SQS_ENABLED="false"
export SQS_QUEUE_URL=""
export CLOUDFRONT_SECRET="local-dev-cloudfront-secret"
export CORS_ALLOWED_ORIGINS="http://localhost:3000"

echo -e "${GREEN}  OK: Environment variables configured${RESET}"
echo ""

# ── 1. Docker Compose (DB + Redis + Kafka + Zipkin) ─────────────

echo -e "${CYAN}[1/3] Starting databases, Redis, Kafka, and Zipkin ...${RESET}"

COMPOSE_FILE="$REPO_ROOT/services-spring/docker-compose.databases.yml"
[ -f "$COMPOSE_FILE" ] || { echo "docker-compose.databases.yml not found at $COMPOSE_FILE"; exit 1; }

docker compose -f "$COMPOSE_FILE" up -d
echo -e "${GREEN}  OK: DB containers started${RESET}"

# Wait for DBs to be ready
echo -e "${GRAY}  Waiting for databases to accept connections ...${RESET}"

DB_PORTS=(5433 5434 5435 5436 5437 9092 9411)
DB_NAMES=(auth-db ticket-db payment-db stats-db community-db kafka zipkin)

for i in "${!DB_PORTS[@]}"; do
  PORT="${DB_PORTS[$i]}"
  NAME="${DB_NAMES[$i]}"
  RETRIES=0
  MAX_RETRIES=30

  while [ $RETRIES -lt $MAX_RETRIES ]; do
    if nc -z localhost "$PORT" 2>/dev/null; then
      echo -e "${GREEN}  OK: $NAME (port $PORT)${RESET}"
      break
    fi
    RETRIES=$((RETRIES + 1))
    if [ $RETRIES -ge $MAX_RETRIES ]; then
      echo "ERROR: $NAME did not become ready within 30 seconds"; exit 1
    fi
    sleep 1
  done
done

# ── 2. Spring Boot Services ───────────────────────────────────────

echo ""
echo -e "${CYAN}[2/3] Starting Spring Boot services ...${RESET}"

SERVICES=(
  "auth-service:3005"
  "ticket-service:3002"
  "payment-service:3003"
  "stats-service:3004"
  "queue-service:3007"
  "community-service:3008"
  "gateway-service:3001"
)

PID_DIR="/tmp"

for entry in "${SERVICES[@]}"; do
  SVC_NAME="${entry%%:*}"
  SVC_PORT="${entry#*:}"
  SVC_PATH="$REPO_ROOT/services-spring/$SVC_NAME"
  GRADLEW="$SVC_PATH/gradlew"

  [ -f "$GRADLEW" ] || { echo "gradlew not found: $GRADLEW"; exit 1; }

  if $BUILD; then
    echo -e "${GRAY}  Building $SVC_NAME ...${RESET}"
    "$GRADLEW" -p "$SVC_PATH" clean build -x test
  fi

  echo -e "${GRAY}  Starting $SVC_NAME (port $SVC_PORT) ...${RESET}"
  "$GRADLEW" -p "$SVC_PATH" bootRun >/tmp/tiketi-"$SVC_NAME".log 2>&1 &
  echo $! > "$PID_DIR/tiketi-$SVC_NAME.pid"
done

# Wait for services to be ready
echo ""
echo -e "${GRAY}  Waiting for services to start ...${RESET}"
sleep 15

for entry in "${SERVICES[@]}"; do
  SVC_NAME="${entry%%:*}"
  SVC_PORT="${entry#*:}"
  RETRIES=0
  MAX_RETRIES=60

  while [ $RETRIES -lt $MAX_RETRIES ]; do
    if curl -sf --max-time 2 "http://localhost:$SVC_PORT/health" >/dev/null 2>&1; then
      echo -e "${GREEN}  OK: $SVC_NAME (port $SVC_PORT)${RESET}"
      break
    fi
    RETRIES=$((RETRIES + 1))
    if [ $RETRIES -ge $MAX_RETRIES ]; then
      echo -e "${YELLOW}  WARN: $SVC_NAME not responding yet (port $SVC_PORT)${RESET}"
    fi
    sleep 2
  done
done

# ── 3. Frontend (optional) ────────────────────────────────────────

if $WITH_FRONTEND; then
  echo ""
  echo -e "${CYAN}[3/3] Starting frontend dev server ...${RESET}"

  WEB_PATH="$REPO_ROOT/apps/web"

  if [ ! -d "$WEB_PATH/node_modules" ]; then
    echo -e "${GRAY}  Installing frontend dependencies ...${RESET}"
    npm --prefix "$WEB_PATH" install
  fi

  NEXT_PUBLIC_API_URL="http://localhost:3001" \
    npm --prefix "$WEB_PATH" run dev >/tmp/tiketi-frontend.log 2>&1 &
  echo $! > "$PID_DIR/tiketi-frontend.pid"
  echo -e "${GREEN}  OK: Frontend dev server starting (port 3000)${RESET}"
else
  echo ""
  echo -e "${GRAY}[3/3] Frontend skipped (use --with-frontend to include)${RESET}"
fi

# ── Summary ───────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}================================================${RESET}"
echo -e "${GREEN}   All services started!${RESET}"
echo -e "${GREEN}================================================${RESET}"
echo ""
echo -e "${WHITE}  Services:${RESET}"
echo -e "${WHITE}    Gateway API:     http://localhost:3001${RESET}"
echo -e "${WHITE}    Auth Service:    http://localhost:3005${RESET}"
echo -e "${WHITE}    Ticket Service:  http://localhost:3002${RESET}"
echo -e "${WHITE}    Payment Service: http://localhost:3003${RESET}"
echo -e "${WHITE}    Stats Service:   http://localhost:3004${RESET}"
echo -e "${WHITE}    Queue Service:   http://localhost:3007${RESET}"
echo -e "${WHITE}    Community Svc:   http://localhost:3008${RESET}"
if $WITH_FRONTEND; then
  echo -e "${WHITE}    Frontend:        http://localhost:3000${RESET}"
fi
echo ""
echo -e "${WHITE}  Infrastructure:${RESET}"
echo -e "${GRAY}    auth-db:       localhost:5433${RESET}"
echo -e "${GRAY}    ticket-db:     localhost:5434${RESET}"
echo -e "${GRAY}    payment-db:    localhost:5435${RESET}"
echo -e "${GRAY}    stats-db:      localhost:5436${RESET}"
echo -e "${GRAY}    community-db:  localhost:5437${RESET}"
echo -e "${GRAY}    redis:         localhost:6379${RESET}"
echo -e "${GRAY}    kafka:         localhost:9092${RESET}"
echo -e "${GRAY}    zipkin:        http://localhost:9411${RESET}"
echo ""
echo -e "${WHITE}  Stop:${RESET}"
echo -e "${CYAN}    ./scripts/stop-all.sh${RESET}"
echo ""
