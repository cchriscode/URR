# Spring Services on Kind (Local)

This guide runs the Spring migration stack on local Kind.

## Prerequisites

- Docker Desktop running
- `kind`, `kubectl`
- Enough resources to build 8 service images + 1 frontend image

## 1) Start stack

From repository root:

```powershell
.\scripts\spring-kind-up.ps1
```

Options:

```powershell
.\scripts\spring-kind-up.ps1 -RecreateCluster
.\scripts\spring-kind-up.ps1 -SkipBuild
.\scripts\spring-kind-up.ps1 -SingleNode              # 단일 노드 (이미지 로드 빠름)
.\scripts\spring-kind-up.ps1 -Services auth-service    # 선택적 빌드
.\scripts\spring-kind-up.ps1 -Parallel 6               # 동시 빌드 수 (기본 4)
```

## 2) Smoke test

```powershell
.\scripts\spring-kind-smoke.ps1
```

Expected:

- `http://localhost:3000` -> `200` (frontend)
- `http://localhost:3001/health` -> `200` (gateway)
- `http://localhost:3001/api/auth/me` -> `401` (route + auth guard working)

## 3) Access

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Gateway API | http://localhost:3001 |
| Grafana | http://localhost:3006 (admin/admin) |

## 4) Useful checks

```powershell
kubectl get pods -n urr-spring
kubectl logs -n urr-spring deployment/gateway-service
kubectl logs -n urr-spring deployment/auth-service
kubectl logs -n urr-spring deployment/frontend
kubectl logs -n urr-spring deployment/grafana
```

## 5) Stop stack

Delete only spring namespace:

```powershell
.\scripts\spring-kind-down.ps1
```

Delete full Kind cluster:

```powershell
.\scripts\spring-kind-down.ps1 -DeleteCluster
```

## One-Command Run

From repository root:

```powershell
.\scripts\spring-kind-dev.ps1
```

Or with npm shortcut:

```powershell
npm run spring:kind:dev
```

Options:

```powershell
.\scripts\spring-kind-dev.ps1 -RecreateCluster
.\scripts\spring-kind-dev.ps1 -SkipBuild
.\scripts\spring-kind-dev.ps1 -SingleNode                          # 빠른 개발
.\scripts\spring-kind-dev.ps1 -Services ticket-service,auth-service # 선택적 빌드
```
