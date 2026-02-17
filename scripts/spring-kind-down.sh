#!/usr/bin/env bash
set -euo pipefail

KIND_CLUSTER_NAME="urr-local"
DELETE_CLUSTER=false

for arg in "$@"; do
  case "$arg" in
    --delete-cluster) DELETE_CLUSTER=true ;;
    --name=*)         KIND_CLUSTER_NAME="${arg#--name=}" ;;
  esac
done

if $DELETE_CLUSTER; then
  kind delete cluster --name "$KIND_CLUSTER_NAME"
  echo "Deleted kind cluster '$KIND_CLUSTER_NAME'."
  exit 0
fi

kubectl delete namespace urr-spring --ignore-not-found=true
echo "Deleted namespace 'urr-spring'."
