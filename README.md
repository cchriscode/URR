# URR — 대규모 동시 접속 티켓 예매 플랫폼

대규모 동시 접속 환경에서 공정한 티켓 예매를 제공하는 온라인 플랫폼. 콘서트/공연 이벤트의 좌석 예매, 스탠딩 티켓 구매, 아티스트 멤버십, 티켓 양도, 커뮤니티 기능을 포함한다.

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 프론트엔드 | Next.js 16, React 19, TypeScript 5.9, Tailwind CSS v4, TanStack Query v5 |
| 백엔드 | Java 21, Spring Boot 3.5.0, Spring Cloud Gateway MVC |
| 데이터베이스 | PostgreSQL 16 (서비스별 독립 DB), Redis 7, DynamoDB |
| 메시징 | Apache Kafka 3.7 (MSK), SQS FIFO |
| 인프라 | AWS EKS (Karpenter), Terraform, Kustomize |
| CI/CD | GitHub Actions, ArgoCD, Argo Rollouts (Blue-Green) |
| CDN/보안 | CloudFront, WAFv2, Lambda@Edge |
| 모니터링 | Prometheus (AMP), Grafana (AMG), Zipkin, Loki |
| 결제 | Toss Payments SDK |
| 테스트 | k6 (부하), Vitest, Playwright, Testcontainers |

---

## 아키텍처 개요

```
                        ┌─ Route53 (DNS)
                        ↓
Internet ──→ CloudFront (WAF + Lambda@Edge) ──→ AWS ALB
                  │                                 │
                  │ /_next/static/* → S3             ├─ /api/* → Gateway Service (3001)
                  │ /vwr/* → S3                     └─ /* → Frontend (3000)
                  │ /vwr-api/* → API Gateway                    │
                  │                              Spring Cloud Gateway
                  │                        ┌──┬──┬──┬──┬──┬──┬──┐
                  │                       Auth Ticket Pay Queue Stats Catalog Community
                  │                        │
                  │                   ┌────┴────┐
                  │                 PostgreSQL  Redis  Kafka(MSK)
                  │
            VWR Tier 1 (서버리스)
            API Gateway → Lambda → DynamoDB
```

### 핵심 설계

| 도전 과제 | 해결 방식 |
|----------|----------|
| 수백만 동시 접속 | 2-Tier 대기열: VWR(Lambda+DynamoDB) → Queue(Redis+Spring Boot) |
| 좌석 이중 예약 방지 | Redis Lua 분산 락 + DB FOR UPDATE + Fencing Token 3중 보호 |
| 대기열 우회 방지 | 3-Tier 토큰: Auth JWT → VWR Token → Entry Token |
| 결제-예약 정합성 | 동기 REST(primary) + Kafka 이벤트(secondary) 이중 경로 |
| 서비스 장애 전파 차단 | Resilience4j Circuit Breaker + Retry + Kafka eventual consistency |

---

## 프로젝트 구조

```
URR/
├── apps/web/                    # Next.js 프론트엔드
├── services-spring/             # Spring Boot 마이크로서비스 (8개)
│   ├── auth-service/            #   인증/JWT/OAuth (port 3005)
│   ├── gateway-service/         #   API 게이트웨이 (port 3001)
│   ├── ticket-service/          #   예매/좌석/예약 (port 3002)
│   ├── payment-service/         #   결제 처리 (port 3003)
│   ├── stats-service/           #   통계/분석 (port 3004)
│   ├── queue-service/           #   대기열 Tier 2 (port 3007)
│   ├── catalog-service/         #   이벤트/아티스트 (port 3009)
│   └── community-service/       #   커뮤니티/뉴스 (port 3008)
├── lambda/                      # AWS Lambda 함수
│   ├── vwr-api/                 #   VWR 대기열 API
│   ├── vwr-counter-advancer/    #   VWR 카운터 증가 (EventBridge)
│   ├── edge-queue-check/        #   CloudFront 토큰 검증
│   └── ticket-worker/           #   SQS 이벤트 소비
├── k8s/spring/                  # Kubernetes 매니페스트
│   ├── base/                    #   공통 Deployment/Service
│   └── overlays/                #   kind / dev / staging / prod
├── terraform/                   # AWS 인프라 (20개 모듈)
│   ├── modules/                 #   VPC, EKS, RDS, MSK, ALB, CloudFront, ...
│   └── environments/            #   prod (19모듈) / staging (15모듈)
├── argocd/                      # ArgoCD Application 매니페스트
├── .github/workflows/           # CI/CD 파이프라인 (14개 워크플로우)
├── tests/                       # k6 부하 테스트 + 카오스 테스트
├── scripts/                     # 로컬 개발 스크립트 (PowerShell + bash)
└── docs/                        # 분석 문서, 배포 가이드
```

---

## 빠른 시작 (로컬 개발)

### 사전 요구사항

- Docker Desktop (4GB+ 메모리)
- kind (Kubernetes in Docker)
- kubectl
- JDK 21+
- Node.js 20+

### 방법 A: Kind 클러스터 (권장)

```powershell
# Windows
.\scripts\spring-kind-dev.ps1

# macOS/Linux
./scripts/spring-kind-dev.sh
```

이 스크립트가 수행하는 작업:
1. Kind 클러스터 생성
2. 모든 서비스 Docker 이미지 빌드
3. 이미지를 Kind 클러스터에 로드
4. Kustomize로 K8s 리소스 배포
5. 포트 포워딩 시작

### 방법 B: Docker Compose + Gradle

```bash
# DB/인프라 시작
cd services-spring && docker compose -f docker-compose.databases.yml up -d

# 서비스 개별 실행
cd auth-service && ./gradlew bootRun
```

### 접속 URL (Kind)

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://localhost:30005 |
| Gateway API | http://localhost:30000 |
| Grafana | http://localhost:30006 (admin/admin) |
| Zipkin | http://localhost:30411 |
| Prometheus | http://localhost:30090 |
| PostgreSQL | localhost:30432 (urr_user/urr_password) |

### 유용한 스크립트

| 스크립트 | 설명 |
|---------|------|
| `spring-kind-dev.ps1` | 올인원: 빌드 + 클러스터 + 배포 |
| `spring-kind-up.ps1` | 클러스터 생성 + 이미지 빌드 + 배포 |
| `spring-kind-down.ps1` | 네임스페이스 정리 (옵션: 클러스터 삭제) |
| `spring-kind-build-load.ps1` | Docker 이미지 빌드/로드만 |
| `spring-kind-smoke.ps1` | 전체 서비스 헬스 체크 |
| `start-port-forwards.ps1` | kubectl 포트 포워딩 |

플래그: `-RecreateCluster`, `-SkipBuild`, `-SingleNode`, `-Services svc1,svc2`, `-Parallel N`

상세 가이드: [QUICK_START.md](QUICK_START.md) | [QUICK_START_SPRING_KIND.md](QUICK_START_SPRING_KIND.md)

---

## 마이크로서비스 구조

### 서비스 목록

| 서비스 | 포트 | DB | 역할 |
|--------|------|-----|------|
| gateway-service | 3001 | Redis | API 라우팅, Rate Limiting, JWT/토큰 검증 |
| ticket-service | 3002 | PostgreSQL + Redis + Kafka | 예매, 좌석 관리, 예약, 양도, 멤버십 |
| payment-service | 3003 | PostgreSQL + Kafka | Toss 결제, 환불, 결제 내역 |
| stats-service | 3004 | PostgreSQL + Kafka | 일별/이벤트별 통계 (Kafka Consumer) |
| auth-service | 3005 | PostgreSQL | JWT 인증, Google OAuth, 토큰 갱신 |
| queue-service | 3007 | Redis + DynamoDB | 대기열 Tier 2, VWR 관리자 API |
| community-service | 3008 | PostgreSQL | 커뮤니티 게시판, 뉴스 |
| catalog-service | 3009 | PostgreSQL | 이벤트 CRUD, 아티스트, 관리자 |

### 통신 패턴

```
[동기 — HTTP REST]
Gateway → 모든 백엔드 서비스 (15개 라우트)
Payment → Ticket (/internal/* + INTERNAL_API_TOKEN)
Queue → Catalog (/internal/*)
Catalog → Auth (/internal/*)

[비동기 — Kafka]
Payment → payment-events → Stats (통계 집계)
Ticket → reservation-events → Stats
Ticket → transfer-events, membership-events
```

---

## 2-Tier 대기열 시스템

티켓 오픈 시 수백만 동시 접속을 처리하기 위한 2단계 대기열 구조.

### Tier 1: VWR (Virtual Waiting Room) — 서버리스

대규모 트래픽을 서버리스로 흡수하는 1차 관문.

```
사용자 → CloudFront → API Gateway → Lambda → DynamoDB
                                        ↓
                              EventBridge (1분) → Counter Advancer Lambda
```

- **인프라**: API Gateway + Lambda(Node.js 20) + DynamoDB(On-Demand)
- **처리량**: Reserved Concurrency 100 (prod), DynamoDB 무제한 스케일링
- **카운터**: DynamoDB 원자적 ADD 연산으로 순번 발급
- **입장**: Counter Advancer가 10초마다 500명씩 servingCounter 증가
- **토큰**: 입장 시 VWR Token(JWT, 10분) 발급 → Tier 2 우선순위로 사용

### Tier 2: Queue Service — Pod 기반

입장 허가된 사용자의 예매 순서를 관리하는 2차 관문.

```
사용자 → Gateway → Queue Service → Redis ZSET
                                      ↓
                          AdmissionWorker (1초 주기, Lua 스크립트)
                                      ↓
                          Entry Token 발급 → 좌석 선택 허가
```

- **인프라**: Spring Boot + Redis Cluster
- **순서 관리**: Redis ZSET (score = VWR position 또는 timestamp)
- **입장 제어**: Lua 스크립트로 원자적 큐→활성 전환 (분산 락)
- **동적 폴링**: 순번에 따라 1초~60초 간격 자동 조절
- **토큰**: Entry Token(JWT, 10분) → Lambda@Edge가 `/api/seats/*`, `/api/reservations/*` 접근 시 검증

---

## 동시성 제어 (좌석 예매)

좌석 이중 예약을 방지하는 3중 보호 메커니즘.

```
Phase 1: Redis Lua 분산 락
  └─ seat:{eventId}:{seatId} HASH + Fencing Token (INCR)
  └─ TTL: 300초, 같은 유저면 연장

Phase 2: DB 비관적 잠금
  └─ SELECT seats FOR UPDATE (행 잠금)
  └─ status 확인 (available → held)

Phase 3: 낙관적 잠금
  └─ @Version 컬럼으로 동시 업데이트 감지

결제 시: payment_verify.lua로 Fencing Token 재검증
```

---

## 인증/보안

### 토큰 흐름

```
[로그인] → Auth JWT (access 30분 + refresh 7일)
    ↓ 예매하기 클릭
[VWR 통과] → VWR Token (10분, 쿠키)
    ↓ 대기열 통과
[Queue 통과] → Entry Token (10분, 쿠키)
    ↓ 좌석 선택/예매
```

### 보안 레이어

| 레이어 | 구현 |
|--------|------|
| CDN/Edge | CloudFront + WAFv2 (Rate Limit + AWS Managed Rules) |
| 토큰 검증 | Lambda@Edge (Entry Token), Gateway Filter Chain (JWT) |
| API 인증 | Spring Security + JWT (HS256) |
| 서비스 간 | INTERNAL_API_TOKEN (timing-safe 비교) + NetworkPolicy |
| 데이터 전송 | TLS: RDS, Redis(transit encryption), Kafka(TLS 9094) |
| 데이터 저장 | 암호화: RDS(AES256), Redis(at-rest), MSK(KMS), S3(AES256) |
| 네트워크 격리 | K8s NetworkPolicy (default-deny + 서비스별 화이트리스트) |
| 토큰 갱신 | Refresh Token Rotation + Family 기반 재사용 감지 |

---

## AWS 인프라 (Terraform)

20개 Terraform 모듈로 Prod/Staging 환경을 관리한다.

### 모듈 구성

| 모듈 | 역할 | Prod | Staging |
|------|------|:----:|:-------:|
| vpc | 5-Tier 서브넷 (Public/App/DB/Cache/Streaming) | O | O |
| vpc-endpoints | 10개 PrivateLink + 2 Gateway | O | O |
| eks | EKS 1.31 + Karpenter + 4 Add-on | O | O |
| iam | 8개 IAM Role | O | O |
| rds | PostgreSQL 16 (Multi-AZ, RDS Proxy, Read Replica) | O | O |
| elasticache | Redis 7.1 (Primary+Replica, Auth Token) | O | O |
| msk | Kafka 3.6.0 (TLS+IAM, 2 Broker) | O | O |
| alb | ALB + Target Group (Gateway/Frontend) | O | O |
| sqs | FIFO Queue + DLQ + CloudWatch Alarm | O | O |
| dynamodb-vwr | Counters + Positions 테이블 | O | O |
| api-gateway-vwr | REST API (3 엔드포인트) | O | O |
| lambda-vwr | VWR API + Counter Advancer | O | O |
| lambda-worker | SQS Consumer (VPC 연결) | O | O |
| monitoring | AMP + AMG (Prometheus/Grafana) | O | O |
| secrets | Secrets Manager (RDS, Redis, JWT, Queue) | O | O |
| cloudfront | 3 Origin + Lambda@Edge + 5 Cache Behavior | O | - |
| waf | WAFv2 (Rate Limit + 3 Managed Rules) | O | - |
| route53 | A Record → CloudFront | O | - |
| ecr | 9 Repository (Immutable Tag, Trivy Scan) | O | - |
| s3 | Frontend + Logs 버킷 (OAC, Lifecycle) | O | O |

### 배포 비용 (추정)

- Staging: ~$350/월
- Production: ~$750/월

상세 배포 가이드: [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)

---

## CI/CD 파이프라인

### GitHub Actions (Reusable Workflow 패턴)

```
Push to main → Unit Test (JDK 21) → Integration Test → Docker Build
    → Trivy Scan (CRITICAL/HIGH 차단) → ECR Push
    → Kustomize 매니페스트 업데이트 → ArgoCD Auto-Sync
    → Discord 알림
```

- **인증**: GitHub OIDC → AWS IAM (장기 자격증명 없음)
- **Prod**: Manual Approval Gate 필수
- **Rollback**: 별도 워크플로우로 이전 태그 복원

### ArgoCD + Argo Rollouts

- **GitOps**: Git 커밋 → ArgoCD가 EKS에 자동 동기화 (prune + selfHeal)
- **Blue-Green**: Prod 4개 서비스 (gateway, ticket, payment, queue)에 Argo Rollouts 적용
- **Health Gate**: AnalysisTemplate으로 5회 × 10초 헬스 체크 통과 후 전환

---

## 모니터링

| 컴포넌트 | 로컬 (Kind) | Prod (AWS) |
|---------|------------|-----------|
| 메트릭 | Prometheus v2.51 (Pod) | Amazon Managed Prometheus |
| 대시보드 | Grafana 10.2.3 (Pod) | Amazon Managed Grafana |
| 트레이싱 | Zipkin 3 (in-memory) | Zipkin (Elasticsearch) |
| 로그 | Loki 2.9 + Promtail (DaemonSet) | CloudWatch Logs |
| 알림 | Grafana Alert → Discord | CloudWatch Alarm → SNS |

- **Spring Actuator**: 모든 서비스가 `/actuator/prometheus`, `/actuator/health` 노출
- **K8s Probes**: Startup(150s) + Readiness(10s) + Liveness(20s) 3단계
- **트레이싱 샘플링**: Kind 100%, Staging 50%, Prod 10%
- **부하 테스트**: k6 (4개 시나리오 + 3개 카오스), 매주 월요일 02:00 UTC 자동 실행

---

## 테스트

### 부하 테스트 (k6)

```bash
# 수동 실행
k6 run tests/load/scenarios/browse-events.js

# GitHub Actions (자동)
# staging 배포 후 browse-events 자동 실행
# 매주 월요일 02:00 UTC 전체 시나리오
```

| 시나리오 | 설명 | VU |
|---------|------|-----|
| browse-events | 이벤트 조회 흐름 | 50→200 |
| booking-flow | 예매 전체 흐름 | 10→50 |
| queue-rush | 대기열 동시 접속 | 100→500 |
| mixed-traffic | 혼합 트래픽 | 50→200 |

### 카오스 테스트

| 시나리오 | 검증 항목 |
|---------|----------|
| service-failure | 서비스 장애 시 graceful degradation (>90%) |
| network-latency | 네트워크 지연 시 응답 시간 |
| redis-failure | Redis 장애 시 폴백 동작 |

---

## 문서

| 문서 | 설명 |
|------|------|
| [QUICK_START.md](QUICK_START.md) | 로컬 개발 빠른 시작 (한국어) |
| [QUICK_START_SPRING_KIND.md](QUICK_START_SPRING_KIND.md) | Kind 개발 가이드 (영어) |
| [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) | AWS 배포 11단계 가이드 |
| [docs/analyze/](docs/analyze/) | 프로젝트 종합 분석 문서 |

### 분석 문서 (docs/analyze/)

| 파일 | 내용 |
|------|------|
| 01_프론트엔드_분석.md | Next.js 구조, 페이지별 분석, API 통신, 커스텀 훅 |
| 02_백엔드_분석.md | MSA 구조, DB 스키마, 통신 방식, 동시성, JWT |
| 03_보안_분석.md | 인증, 네트워크, 암호화, WAF, 토큰 아키텍처 |
| 04_인프라_분석.md | Terraform 20개 모듈, K8s 배포, CI/CD, GitOps |
| 05_모니터링_분석.md | Prometheus, Grafana, Zipkin, Loki, 알림, 부하 테스트 |
| 06_사용자플로우_분석.md | 로그인→이벤트→VWR→큐→좌석→결제 전체 흐름 |
| 07_아키텍처_비교분석.md | ALB vs Nginx vs Istio 비교, 선택 이유 |
| 08_서비스간_보안_분석.md | mTLS 부재, NetworkPolicy, 토큰 보안 |
