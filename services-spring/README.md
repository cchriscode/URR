# Spring Migration Workspace

This folder contains the Spring Boot stack for incremental replacement.

## Services
- `gateway-service` (port `3001`)
- `ticket-service` (port `3002`)
- `payment-service` (port `3003`)
- `stats-service` (port `3004`)
- `auth-service` (port `3005`)

## Local DB Split
Use `docker-compose.databases.yml` for physically split databases:
- `auth_db` on `5433`
- `ticket_db` on `5434`
- `payment_db` on `5435`
- `stats_db` on `5436`

## Run Order
1. `docker compose -f services-spring/docker-compose.databases.yml up -d`
2. Start services with `services-spring/scripts/start-local.ps1`
3. Point frontend to gateway: `NEXT_PUBLIC_API_URL=http://localhost:3001`

## Status
- `auth-service`: core endpoints + Google login + internal user lookup endpoints implemented
- `ticket-service`: DB-backed compatibility endpoints implemented (events/seats/reservations/queue/admin/news/image)
- `payment-service`: DB-backed compatibility endpoints implemented
- `stats-service`: admin-guarded compatibility endpoints implemented
- `gateway-service`: routing to Spring services implemented

## Remaining Tasks
- Add contract/integration parity tests
- Add Redis ZSET queue manager for production queue behavior

## Local kind Run
- Overlay: `k8s/spring/overlays/kind`
- One-command up: `scripts/spring-kind-up.ps1`
- Smoke test: `scripts/spring-kind-smoke.ps1`
- Detailed guide: `services-spring/KIND_QUICK_START.md`
