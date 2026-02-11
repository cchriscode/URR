#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="tiketi-spring"

for arg in "$@"; do
  case "$arg" in
    --namespace=*) NAMESPACE="${arg#--namespace=}" ;;
  esac
done

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
RESET='\033[0m'

PID_DIR="/tmp"

echo ""
echo -e "${CYAN}================================================${RESET}"
echo -e "${CYAN}   TIKETI Spring - Port Forward Setup${RESET}"
echo -e "${CYAN}================================================${RESET}"
echo ""

# ── Prerequisites ─────────────────────────────────────────────────

command -v kubectl >/dev/null 2>&1 || { echo "kubectl is not installed."; exit 1; }

echo -e "${CYAN}Testing cluster connection ...${RESET}"
kubectl cluster-info >/dev/null 2>&1 || { echo "Cannot connect to Kubernetes cluster. Is Kind running?"; exit 1; }
echo -e "${GREEN}  OK: Cluster connection${RESET}"
echo ""

# ── Kill existing port-forwards ───────────────────────────────────

echo -e "${YELLOW}Stopping existing port-forwards ...${RESET}"
pkill -f "kubectl port-forward.*$NAMESPACE" 2>/dev/null || true
sleep 1

# ── Check port availability ───────────────────────────────────────

echo -e "${CYAN}Checking port availability ...${RESET}"
REQUIRED_PORTS=(3000 3001 3002 3003 3004 3005)
PORTS_IN_USE=()

for port in "${REQUIRED_PORTS[@]}"; do
  if lsof -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    PID=$(lsof -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -1)
    PROC=$(ps -p "$PID" -o comm= 2>/dev/null || echo "unknown")
    echo -e "${YELLOW}  WARN: Port $port in use by $PROC (PID: $PID)${RESET}"
    PORTS_IN_USE+=("$port")
  fi
done

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
  echo ""
  echo -e "${YELLOW}  Some ports are in use. Port-forwards for those may fail.${RESET}"
  echo -e "${GRAY}  Continuing in 3 seconds ...${RESET}"
  sleep 3
else
  echo -e "${GREEN}  OK: All ports available${RESET}"
fi

# ── Start port-forwards ───────────────────────────────────────────

echo ""
echo -e "${GREEN}Starting port-forwards ...${RESET}"
echo ""

FORWARDS=(
  "Gateway:svc/gateway-service:3001:3001"
  "Auth:svc/auth-service:3005:3005"
  "Ticket:svc/ticket-service:3002:3002"
  "Payment:svc/payment-service:3003:3003"
  "Stats:svc/stats-service:3004:3004"
  "Frontend:svc/frontend-service:3000:3000"
)

TOTAL=${#FORWARDS[@]}
INDEX=1

for fwd in "${FORWARDS[@]}"; do
  NAME="${fwd%%:*}"
  REST="${fwd#*:}"
  SERVICE="${REST%%:*}"
  REST2="${REST#*:}"
  LOCAL_PORT="${REST2%%:*}"
  REMOTE_PORT="${REST2#*:}"

  echo -e "${CYAN}  [$INDEX/$TOTAL] $NAME Service ($LOCAL_PORT)${RESET}"
  kubectl port-forward --address 0.0.0.0 -n "$NAMESPACE" "$SERVICE" "$LOCAL_PORT:$REMOTE_PORT" \
    >/dev/null 2>&1 &
  echo $! > "$PID_DIR/tiketi-pf-$LOCAL_PORT.pid"
  sleep 2
  INDEX=$((INDEX + 1))
done

# ── Health check ──────────────────────────────────────────────────

echo ""
echo -e "${CYAN}Verifying services (waiting 5s) ...${RESET}"
sleep 5

ALL_HEALTHY=true
for fwd in "${FORWARDS[@]}"; do
  NAME="${fwd%%:*}"
  REST="${fwd#*:}"
  SERVICE="${REST%%:*}"
  REST2="${REST#*:}"
  LOCAL_PORT="${REST2%%:*}"

  # Try /actuator/health then /health
  if curl -sf --max-time 3 "http://localhost:$LOCAL_PORT/actuator/health" >/dev/null 2>&1 \
    || curl -sf --max-time 3 "http://localhost:$LOCAL_PORT/health" >/dev/null 2>&1; then
    echo -e "${GREEN}  OK: $NAME (port $LOCAL_PORT)${RESET}"
  else
    echo -e "${YELLOW}  WARN: $NAME (port $LOCAL_PORT) - not responding yet${RESET}"
    ALL_HEALTHY=false
  fi
done

# ── Summary ───────────────────────────────────────────────────────

echo ""
if $ALL_HEALTHY; then
  echo -e "${GREEN}================================================${RESET}"
  echo -e "${GREEN}   All port-forwards are active!${RESET}"
  echo -e "${GREEN}================================================${RESET}"
else
  echo -e "${YELLOW}================================================${RESET}"
  echo -e "${YELLOW}   Some services not ready yet.${RESET}"
  echo -e "${YELLOW}   They may still be starting. Try again in a minute.${RESET}"
  echo -e "${YELLOW}================================================${RESET}"
fi
echo ""
echo -e "${WHITE}  Frontend:        http://localhost:3000${RESET}"
echo -e "${WHITE}  Gateway API:     http://localhost:3001${RESET}"
echo -e "${WHITE}  Auth Service:    http://localhost:3005${RESET}"
echo -e "${WHITE}  Ticket Service:  http://localhost:3002${RESET}"
echo -e "${WHITE}  Payment Service: http://localhost:3003${RESET}"
echo -e "${WHITE}  Stats Service:   http://localhost:3004${RESET}"
echo ""
echo -e "${GRAY}  Port-forwards run in the background.${RESET}"
echo -e "${GRAY}  To stop: pkill -f 'kubectl port-forward'${RESET}"
echo ""
