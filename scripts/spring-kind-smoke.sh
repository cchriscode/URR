#!/usr/bin/env bash
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
RESET='\033[0m'

assert_endpoint() {
  local url="$1"
  local expected="$2"

  local actual
  actual=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")

  if [ "$actual" != "$expected" ]; then
    echo -e "${RED}FAIL${RESET}: $url  expected=$expected actual=$actual"
    exit 1
  fi
  echo -e "${GREEN}OK${RESET}: $url ($actual)"
}

kubectl get pods -n urr-spring

assert_endpoint "http://localhost:3001/health"       "200"
assert_endpoint "http://localhost:3001/api/auth/me"  "401"
assert_endpoint "http://localhost:3000"               "200"

echo ""
echo -e "${GREEN}Smoke checks passed.${RESET}"
