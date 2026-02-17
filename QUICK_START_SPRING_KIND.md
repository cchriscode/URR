# Spring Kind Quick Start

## Windows (PowerShell)

### Prerequisites

- Docker Desktop (running)
- [kind](https://kind.sigs.k8s.io/) (`winget install Kubernetes.kind`)
- kubectl (`winget install Kubernetes.kubectl`)
- JDK 21+, Node.js 20+

### Run the full stack

```powershell
cd C:\Users\USER\project-ticketing-copy

# Deploy everything to Kind (builds Docker images + deploys to k8s)
.\scripts\spring-kind-dev.ps1

# Verify
.\scripts\spring-kind-smoke.ps1
```

### Individual steps

```powershell
# 1. Create cluster + build images + deploy
.\scripts\spring-kind-up.ps1

# 2. Build & reload images only (after code changes)
.\scripts\spring-kind-build-load.ps1

# 3. Tear down namespace (keep cluster)
.\scripts\spring-kind-down.ps1

# 4. Tear down and delete cluster
.\scripts\spring-kind-down.ps1 -DeleteCluster

# 5. Set up kubectl port-forwards (for direct service access)
.\scripts\start-port-forwards.ps1
```

### Local dev without Kind (Docker Compose + Gradle)

```powershell
# Start DBs + all Spring Boot services
.\scripts\start-all.ps1

# With build step and frontend dev server
.\scripts\start-all.ps1 -Build -WithFrontend

# Stop everything
.\scripts\stop-all.ps1

# Full cleanup (removes cluster, images, caches)
.\scripts\cleanup.ps1
```

---

## macOS / Linux (bash)

### Prerequisites

Install the required tools if not already present:

```bash
# Homebrew (macOS)
brew install kind kubectl docker

# Or install kind manually
curl -Lo ~/bin/kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-darwin-amd64
chmod +x ~/bin/kind
```

### Run the full stack

```bash
cd /path/to/project
chmod +x scripts/*.sh

# Deploy everything to Kind (single node - recommended for local dev)
./scripts/spring-kind-dev.sh --single-node

# Verify
./scripts/spring-kind-smoke.sh
```

### Individual steps

```bash
# 1. Create cluster + build images + deploy
./scripts/spring-kind-up.sh --single-node

# 2. Build & reload images only (after code changes)
./scripts/spring-kind-build-load.sh

# 3. Selective rebuild (changed services only)
./scripts/spring-kind-build-load.sh --services auth-service,ticket-service

# 4. Tear down namespace (keep cluster)
./scripts/spring-kind-down.sh

# 5. Tear down and delete cluster
./scripts/spring-kind-down.sh --delete-cluster

# 6. Set up kubectl port-forwards (for direct service access)
./scripts/start-port-forwards.sh
```

### Local dev without Kind (Docker Compose + Gradle)

```bash
# Start DBs + all Spring Boot services
./scripts/start-all.sh

# With build step and frontend dev server
./scripts/start-all.sh --build --with-frontend

# Stop everything
./scripts/stop-all.sh

# Full cleanup (removes cluster, images, caches)
./scripts/cleanup.sh
```

---

## Script flags

| Script | PowerShell | bash | 설명 |
|---|---|---|---|
| `spring-kind-dev` | `-RecreateCluster` | `--recreate-cluster` | 클러스터 재생성 |
| | `-SkipBuild` | `--skip-build` | 빌드 건너뛰기 |
| | `-SingleNode` | `--single-node` | 단일 노드 (이미지 로드 3배 빠름) |
| | `-Services svc1,svc2` | `--services svc1,svc2` | 선택적 빌드 |
| | `-Parallel N` | `--parallel N` | 동시 빌드 수 (기본 4) |
| `spring-kind-up` | 위 옵션 모두 지원 | 위 옵션 모두 지원 | |
| `spring-kind-build-load` | `-Services`, `-Parallel` | `--services`, `--parallel` | 빌드+로드만 |
| `spring-kind-down` | `-DeleteCluster` | `--delete-cluster` | 정리 |
| `start-all` | `-Build`, `-WithFrontend` | `--build`, `--with-frontend` | 로컬 개발 |
| `cleanup` | `-Force` | `--force` | 전체 초기화 |

## Services

| Service | Port | Description |
|---|---|---|
| frontend | 3000 | Next.js web app |
| gateway-service | 3001 | API Gateway (external entry point) |
| ticket-service | 3002 | Booking, reservations, seats |
| payment-service | 3003 | Payment processing |
| stats-service | 3004 | Analytics |
| auth-service | 3005 | Authentication + JWT |
| grafana | 3006 | Monitoring dashboard (admin/admin) |
| queue-service | 3007 | Virtual waiting room |
| community-service | 3008 | Community posts |
| catalog-service | 3009 | Events, artists, admin |

Gateway URL: `http://localhost:3001`

Detailed guide: `services-spring/KIND_QUICK_START.md`
