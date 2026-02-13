# Spring kind Quick Start

## Windows (PowerShell)

```powershell
cd C:\Users\USER\project-ticketing-copy
.\scripts\spring-kind-up.ps1
.\scripts\spring-kind-smoke.ps1
```

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

# Deploy everything to Kind (builds Docker images + deploys to k8s)
./scripts/spring-kind-dev.sh

# Verify
./scripts/spring-kind-smoke.sh
```

### Individual steps

```bash
# 1. Create cluster + build images + deploy
./scripts/spring-kind-up.sh

# 2. Build & reload images only (after code changes)
./scripts/spring-kind-build-load.sh

# 3. Tear down namespace (keep cluster)
./scripts/spring-kind-down.sh

# 4. Tear down and delete cluster
./scripts/spring-kind-down.sh --delete-cluster

# 5. Set up kubectl port-forwards (for direct service access)
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

### Script flags

| Script | Flags |
|---|---|
| `spring-kind-dev.sh` | `--recreate-cluster`, `--skip-build` |
| `spring-kind-up.sh` | `--recreate-cluster`, `--skip-build`, `--name=<cluster>` |
| `spring-kind-build-load.sh` | `--name=<cluster>` |
| `spring-kind-down.sh` | `--delete-cluster`, `--name=<cluster>` |
| `start-all.sh` | `--build`, `--with-frontend` |
| `cleanup.sh` | `--force` (skip confirmation prompts) |

### Services

| Service | Port | Description |
|---|---|---|
| gateway-service | 3001 | API Gateway (external entry point) |
| auth-service | 3005 | Authentication + JWT |
| ticket-service | 3002 | Booking, reservations, seats |
| catalog-service | 3009 | Events, artists, admin |
| payment-service | 3003 | Payment processing |
| stats-service | 3004 | Analytics |
| queue-service | 3007 | Virtual waiting room |
| community-service | 3008 | News, community |
| frontend | 3000 | Next.js web app |

Gateway URL: `http://localhost:3001`

Detailed guide: `services-spring/KIND_QUICK_START.md`
