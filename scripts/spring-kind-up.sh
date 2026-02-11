#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RESET='\033[0m'

KIND_CLUSTER_NAME="tiketi-local"
RECREATE_CLUSTER=false
SKIP_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --recreate-cluster) RECREATE_CLUSTER=true ;;
    --skip-build)       SKIP_BUILD=true ;;
    --name=*)           KIND_CLUSTER_NAME="${arg#--name=}" ;;
  esac
done

# ── Prerequisites ─────────────────────────────────────────────────

command -v kind    >/dev/null 2>&1 || { echo "kind is not installed."; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "kubectl is not installed."; exit 1; }
command -v docker  >/dev/null 2>&1 || { echo "docker is not installed."; exit 1; }

docker info >/dev/null 2>&1 || { echo "Docker daemon is not running. Start Docker Desktop first."; exit 1; }

# ── Cluster setup ─────────────────────────────────────────────────

CLUSTER_EXISTS=false
if kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER_NAME"; then
  CLUSTER_EXISTS=true
fi

if $RECREATE_CLUSTER && $CLUSTER_EXISTS; then
  echo "Deleting existing kind cluster '$KIND_CLUSTER_NAME' ..."
  kind delete cluster --name "$KIND_CLUSTER_NAME"
  CLUSTER_EXISTS=false
fi

if ! $CLUSTER_EXISTS; then
  echo "Creating kind cluster '$KIND_CLUSTER_NAME' ..."
  kind create cluster --name "$KIND_CLUSTER_NAME" --config "$REPO_ROOT/kind-config.yaml"
fi

kubectl config use-context "kind-$KIND_CLUSTER_NAME" >/dev/null

# ── Build & load images ───────────────────────────────────────────

if ! $SKIP_BUILD; then
  bash "$SCRIPT_DIR/spring-kind-build-load.sh" --name="$KIND_CLUSTER_NAME"
fi

# ── Deploy ────────────────────────────────────────────────────────

echo "Applying spring kind overlay ..."
kubectl apply -k "$REPO_ROOT/k8s/spring/overlays/kind"

# ── Wait for rollouts ─────────────────────────────────────────────

NAMESPACE="tiketi-spring"
DEPLOYMENTS=(
  postgres-spring
  dragonfly-spring
  auth-service
  ticket-service
  payment-service
  stats-service
  gateway-service
  frontend
  loki
  grafana
)

for deployment in "${DEPLOYMENTS[@]}"; do
  echo "Waiting for deployment/$deployment ..."
  kubectl rollout status "deployment/$deployment" -n "$NAMESPACE" --timeout=300s
done

echo ""
echo "Current pods:"
kubectl get pods -n "$NAMESPACE" -o wide

echo ""
echo -e "${GREEN}Frontend:  http://localhost:3000${RESET}"
echo -e "${GREEN}Gateway:   http://localhost:3001${RESET}"
echo -e "${GREEN}Grafana:   http://localhost:3006 (admin/admin)${RESET}"
echo -e "${CYAN}Health:    http://localhost:3001/health${RESET}"
echo "Namespace: $NAMESPACE"
