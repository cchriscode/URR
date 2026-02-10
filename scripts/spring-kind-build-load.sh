#!/usr/bin/env bash
set -euo pipefail

KIND_CLUSTER_NAME="${1:-tiketi-local}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! kind get clusters | grep -qx "${KIND_CLUSTER_NAME}"; then
  echo "Kind cluster '${KIND_CLUSTER_NAME}' not found. Create it first."
  exit 1
fi

build_and_load() {
  local service_name="$1"
  local image="$2"
  local service_path="${REPO_ROOT}/services-spring/${service_name}"

  echo "Building image ${image} from ${service_path} ..."
  docker build -t "${image}" -f "${service_path}/Dockerfile" "${service_path}"
  echo "Loading image ${image} into kind cluster ${KIND_CLUSTER_NAME} ..."
  kind load docker-image "${image}" --name "${KIND_CLUSTER_NAME}"
}

build_and_load "gateway-service" "tiketi-spring-gateway-service:local"
build_and_load "auth-service" "tiketi-spring-auth-service:local"
build_and_load "ticket-service" "tiketi-spring-ticket-service:local"
build_and_load "payment-service" "tiketi-spring-payment-service:local"
build_and_load "stats-service" "tiketi-spring-stats-service:local"

echo "Spring images are built and loaded into kind."
