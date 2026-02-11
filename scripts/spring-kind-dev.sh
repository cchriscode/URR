#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

GREEN='\033[0;32m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
CYAN='\033[0;36m'
RESET='\033[0m'

RECREATE_CLUSTER=false
SKIP_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --recreate-cluster) RECREATE_CLUSTER=true ;;
    --skip-build)       SKIP_BUILD=true ;;
  esac
done

echo "Starting full stack on kind ..."

UP_ARGS=()
$RECREATE_CLUSTER && UP_ARGS+=(--recreate-cluster)
$SKIP_BUILD       && UP_ARGS+=(--skip-build)

bash "$SCRIPT_DIR/spring-kind-up.sh" "${UP_ARGS[@]+"${UP_ARGS[@]}"}"

echo ""
echo -e "${GREEN}================================================${RESET}"
echo -e "${GREEN}   All services deployed to Kind!${RESET}"
echo -e "${GREEN}================================================${RESET}"
echo ""
echo -e "${WHITE}  Frontend:  http://localhost:3000${RESET}"
echo -e "${WHITE}  Gateway:   http://localhost:3001${RESET}"
echo -e "${WHITE}  Grafana:   http://localhost:3006 (admin/admin)${RESET}"
echo ""
echo -e "${GRAY}  Kind NodePort mapping handles port access.${RESET}"
echo -e "${GRAY}  For individual service access, run:${RESET}"
echo -e "${CYAN}    ./scripts/start-port-forwards.sh${RESET}"
echo ""
