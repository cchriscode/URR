#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

KIND_CLUSTER_NAME="tiketi-local"

for arg in "$@"; do
  case "$arg" in
    --name=*) KIND_CLUSTER_NAME="${arg#--name=}" ;;
    # positional fallback for backwards compat
    tiketi-*) KIND_CLUSTER_NAME="$arg" ;;
  esac
done

# ── Prerequisites ─────────────────────────────────────────────────

command -v kind   >/dev/null 2>&1 || { echo "kind is not installed."; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is not installed."; exit 1; }

docker info >/dev/null 2>&1 || { echo "Docker daemon is not running. Start Docker Desktop first."; exit 1; }

kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER_NAME" \
  || { echo "Kind cluster '$KIND_CLUSTER_NAME' not found. Create it first."; exit 1; }

# ── Spring Boot services ──────────────────────────────────────────

SERVICES=(
  "gateway-service:tiketi-spring-gateway-service:local"
  "auth-service:tiketi-spring-auth-service:local"
  "ticket-service:tiketi-spring-ticket-service:local"
  "payment-service:tiketi-spring-payment-service:local"
  "stats-service:tiketi-spring-stats-service:local"
)

for entry in "${SERVICES[@]}"; do
  SVC_NAME="${entry%%:*}"
  IMAGE="${entry#*:}"
  SVC_PATH="$REPO_ROOT/services-spring/$SVC_NAME"
  DOCKERFILE="$SVC_PATH/Dockerfile"

  [ -f "$DOCKERFILE" ] || { echo "Dockerfile not found: $DOCKERFILE"; exit 1; }

  echo "Building image $IMAGE from $SVC_PATH ..."
  docker build -t "$IMAGE" -f "$DOCKERFILE" "$SVC_PATH"

  echo "Loading image $IMAGE into kind cluster $KIND_CLUSTER_NAME ..."
  kind load docker-image "$IMAGE" --name "$KIND_CLUSTER_NAME"
done

# ── Frontend ──────────────────────────────────────────────────────

FRONTEND_PATH="$REPO_ROOT/apps/web"
FRONTEND_DOCKERFILE="$FRONTEND_PATH/Dockerfile"
FRONTEND_IMAGE="tiketi-spring-frontend:local"

[ -f "$FRONTEND_DOCKERFILE" ] || { echo "Frontend Dockerfile not found: $FRONTEND_DOCKERFILE"; exit 1; }

echo "Building frontend image $FRONTEND_IMAGE ..."
docker build -t "$FRONTEND_IMAGE" \
  --build-arg NEXT_PUBLIC_API_URL=http://localhost:3001 \
  -f "$FRONTEND_DOCKERFILE" "$FRONTEND_PATH"

echo "Loading frontend image into kind cluster $KIND_CLUSTER_NAME ..."
kind load docker-image "$FRONTEND_IMAGE" --name "$KIND_CLUSTER_NAME"

echo "All images are built and loaded into kind."
