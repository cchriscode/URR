# TIKETI Spring - Quick Start

`C:\Users\USER\project-ticketing-copy` 기준.

---

## 방법 A: Kind (Kubernetes 로컬) - 권장

전체 스택(Frontend + Backend + DB + Monitoring)을 Kind 클러스터에 배포. 기존 프로젝트와 동일한 방식.

### 한 줄 실행

```powershell
.\scripts\spring-kind-dev.ps1
```

### 옵션

```powershell
.\scripts\spring-kind-dev.ps1 -RecreateCluster   # 클러스터 재생성
.\scripts\spring-kind-dev.ps1 -SkipBuild          # 이미지 빌드 건너뛰기
```

### 단계별 실행

```powershell
# 1. 전체 스택 배포 (빌드 + K8s 배포)
.\scripts\spring-kind-up.ps1

# 2. 개별 서비스 포트 포워딩 (필요 시)
.\scripts\start-port-forwards.ps1

# 3. 헬스체크
.\scripts\spring-kind-smoke.ps1
```

### 접속 (Kind NodePort)

| 서비스 | URL | NodePort |
|--------|-----|----------|
| Frontend (Next.js) | http://localhost:3000 | 30005 |
| Gateway API | http://localhost:3001 | 30000 |
| Grafana | http://localhost:3006 | 30006 |
| Zipkin (분산 추적) | http://localhost:9411 | 30411 |
| PostgreSQL | localhost:15432 | 30432 |

Grafana 로그인: admin / admin

### 종료

```powershell
.\scripts\spring-kind-down.ps1              # 네임스페이스만 삭제
.\scripts\spring-kind-down.ps1 -DeleteCluster  # 클러스터 전체 삭제
```

---

## 방법 B: 로컬 개발 (Docker Compose + Gradle)

DB/Redis/Kafka/Zipkin은 Docker, Spring 서비스는 로컬 Gradle로 실행. 코드 수정 시 빠른 반영 가능.

> `start-all.ps1`이 로컬 개발용 시크릿(JWT_SECRET, INTERNAL_API_TOKEN 등)을 자동으로 환경변수에 설정합니다. 별도 `.env` 파일은 필요 없습니다.

### 한 줄 실행

```powershell
.\scripts\start-all.ps1 -Build -WithFrontend
```

### 옵션

```powershell
.\scripts\start-all.ps1                     # 백엔드만 (빌드 없이)
.\scripts\start-all.ps1 -Build              # 빌드 후 실행
.\scripts\start-all.ps1 -WithFrontend       # 프론트엔드 포함
.\scripts\start-all.ps1 -Build -WithFrontend # 빌드 + 프론트엔드
```

### 종료

```powershell
.\scripts\stop-all.ps1
```

---

## 포트 정리

| 서비스 | 포트 | 설명 |
|--------|------|------|
| Frontend (Next.js) | 3000 | 프론트엔드 |
| Gateway (Spring Cloud) | 3001 | API 게이트웨이 (VWR + Rate Limiting) |
| Ticket Service | 3002 | 이벤트/좌석/예매/양도/멤버십 |
| Payment Service | 3003 | 결제 |
| Stats Service | 3004 | 통계/대시보드 |
| Auth Service | 3005 | 인증/회원 |
| Grafana | 3006 | 모니터링 대시보드 |
| Queue Service | 3007 | 대기열/VWR (Redis only) |
| Community Service | 3008 | 뉴스 게시판 |
| Management (전 서비스) | 9090 | Actuator/Prometheus 메트릭 |

> **참고**: `/actuator/*` 엔드포인트(health, prometheus, info)는 management port **9090**에서만 접근 가능. 앱 포트의 `/health` 엔드포인트는 정상 동작.

Docker Compose 인프라 포트 (로컬 개발):

| 서비스 | 포트 | 설명 |
|--------|------|------|
| auth-db | 5433 | auth_db |
| ticket-db | 5434 | ticket_db |
| payment-db | 5435 | payment_db |
| stats-db | 5436 | stats_db |
| community-db | 5437 | community_db |
| Redis | 6379 | 대기열/캐시 |
| Kafka | 9092 | 메시지 브로커 (KRaft) |
| Zipkin | 9411 | 분산 추적 UI |

---

## 검증

```powershell
# 스모크 테스트
.\scripts\spring-kind-smoke.ps1

# 수동 확인
curl http://localhost:3000          # Frontend
curl http://localhost:3001/health   # Gateway
curl http://localhost:3001/api/auth/me  # 인증 (401 정상)
curl http://localhost:3001/api/events   # 이벤트 목록
curl http://localhost:9411          # Zipkin UI (분산 추적)
```

---

## 스크립트 목록

| 스크립트 | 설명 |
|----------|------|
| `scripts\spring-kind-dev.ps1` | Kind 전체 스택 올인원 |
| `scripts\spring-kind-up.ps1` | Kind 클러스터 배포 |
| `scripts\spring-kind-down.ps1` | Kind 정리 |
| `scripts\spring-kind-build-load.ps1` | Docker 이미지 빌드/로드 (Frontend 포함) |
| `scripts\spring-kind-smoke.ps1` | Kind 헬스체크 |
| `scripts\start-port-forwards.ps1` | Kind 포트 포워딩 (Frontend 포함) |
| `scripts\start-all.ps1` | 로컬 개발 실행 (Docker Compose + Gradle) |
| `scripts\stop-all.ps1` | 로컬 개발 종료 |
| `scripts\cleanup.ps1` | 전체 초기화 |

---

## 전체 초기화 (Cleanup)

```powershell
.\scripts\cleanup.ps1          # 대화형
.\scripts\cleanup.ps1 -Force   # 전부 삭제
```

---

## 트러블슈팅

### 포트 충돌

```powershell
netstat -ano | findstr :3001
```

### Kind Pod 상태

```powershell
kubectl get pods -n tiketi-spring
kubectl logs -n tiketi-spring deployment/gateway-service --tail=50
kubectl logs -n tiketi-spring deployment/frontend --tail=50
kubectl rollout restart deployment/auth-service -n tiketi-spring
```

### Docker 컨테이너 상태 (로컬 개발)

```powershell
docker compose -f services-spring\docker-compose.databases.yml ps
docker compose -f services-spring\docker-compose.databases.yml logs auth-db
docker compose -f services-spring\docker-compose.databases.yml logs kafka
docker compose -f services-spring\docker-compose.databases.yml logs zipkin
```
