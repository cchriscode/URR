# URR 프로젝트 종합 분석 보고서

> 작성일: 2026-02-16
> 대상: URR (Ultimate Reservation & Retail) -- 온라인 티켓 예매 플랫폼
> 범위: 프론트엔드, 백엔드, 대기열, 보안, 인프라, 모니터링, 사용자 플로우 전 영역

---

## 1. 프로젝트 개요

URR은 대규모 동시 접속 환경에서 공정한 티켓 예매를 제공하는 온라인 플랫폼이다. 콘서트/공연 이벤트의 좌석 예매, 스탠딩 티켓 구매, 아티스트 멤버십, 티켓 양도, 커뮤니티 기능을 포함한다.

### 핵심 기술적 도전 과제

| 도전 과제 | 해결 방식 | 상세 문서 |
|----------|----------|----------|
| 수백만 동시 접속 처리 | 2-Tier VWR: CDN 엣지(Tier 1) + Redis(Tier 2) 대기열 | `03-vwr-queue.md` |
| 좌석 이중 예약 방지 | Redis Lua 분산 락 + DB 낙관적 잠금 + Fencing Token 3중 보호 | `02-backend.md` |
| 공정한 대기 순서 보장 | DynamoDB 원자적 카운터(Tier 1) + Redis ZSET(Tier 2) | `03-vwr-queue.md` |
| 결제-예약 정합성 | 동기 REST(primary) + Kafka 이벤트(secondary) 이중 경로 | `02-backend.md`, `07-user-flow.md` |
| 대기열 우회 방지 | 3-Tier 토큰 아키텍처 (Auth JWT + VWR Token + Entry Token) | `04-auth-security.md` |
| 서비스 장애 전파 차단 | Resilience4j Circuit Breaker + Retry + Kafka eventual consistency | `02-backend.md`, `07-user-flow.md` |

### 전체 기술 스택 요약

| 영역 | 기술 |
|------|------|
| 프론트엔드 | Next.js 16, React 19, TypeScript, Tailwind CSS v4, TanStack React Query |
| 백엔드 | Java 21, Spring Boot 3.5.0, Spring Cloud Gateway MVC |
| 데이터베이스 | PostgreSQL 16 (4개 독립 DB), Redis 7, DynamoDB |
| 메시징 | Apache Kafka 3.7.0, AWS SQS FIFO |
| 인프라 | AWS EKS, RDS, ElastiCache, MSK, CloudFront, Lambda@Edge, S3 |
| IaC | Terraform (18개 모듈), Kustomize (4개 환경) |
| CI/CD | GitHub Actions (15개 워크플로), ArgoCD (3개 환경), Argo Rollouts |
| 모니터링 | Micrometer + Prometheus + Grafana, Brave + Zipkin, Promtail + Loki |
| 결제 | TossPayments SDK |
| 보안 | JWT (HMAC-SHA256), BCrypt, Secrets Manager, KMS |

---

## 2. 아키텍처 전체 조감도

```
                                [사용자 브라우저]
                                       |
                                  [Route 53]
                                       |
                              +--[CloudFront CDN]--+
                              |        |           |
                     Lambda@Edge    CF Function   S3 (정적 자산)
                   (토큰 검증)   (VWR 리라이트)   (프론트엔드, VWR)
                              |        |
              +---------------+        +----------+
              |                                   |
     VWR 활성 시: 302 /vwr/{eventId}       [ALB (HTTPS)]
              |                           CloudFront Prefix List
     +--[API Gateway]--+                  접근만 허용
     |                 |                         |
  [Lambda:             |                +--------+--------+
   VWR API]            |                |   App Subnets    |
     |                 |                |   (EKS Nodes)    |
  [DynamoDB]    [Lambda:                |                  |
  (counters,    Counter Advancer]       |  [Gateway :3001] |
   positions)   (EventBridge 1분)       |   JWT 검증        |
                                        |   Rate Limiting   |
                                        |   Entry Token 검증|
                                        |        |         |
               +--------+--------+------+--------+---------+--------+
               |        |        |      |        |         |        |
           [Auth]  [Ticket]  [Payment] [Queue] [Stats] [Catalog] [Community]
           :3005   :3002     :3003     :3007   :3004   :3009     :3008
               |        |        |      |        |         |        |
           auth_db  ticket_db  payment_db  Redis  stats_db  ticket_db  community_db
           (5438)   (5434)    (5435)       |     (5436)   (읽기전용)  (5437)
                        |        |         |
                        +---[Kafka/MSK]----+
                        |  (payment-events, reservation-events,  |
                        |   transfer-events, membership-events)  |
                        +----------------------------------------+
                                        |
                              +----[SQS FIFO]----+
                              |      (입장 이벤트) |
                              +--[SQS DLQ]-------+
```

### 2-Tier VWR (Virtual Waiting Room) 구조

```
Tier 1 (CDN 서버리스)                     Tier 2 (애플리케이션)
+-----------------------------------+     +-----------------------------------+
| CloudFront + Lambda@Edge          |     | EKS + queue-service               |
| S3 정적 대기 페이지                 | --> | Redis ZSET 기반 대기열              |
| DynamoDB 원자적 카운터              |     | 1초 주기 배치 입장                  |
| 10초마다 500명 입장 (50명/초)       |     | threshold(1000) 기반 동시접속 제한  |
| 익명 사용자 (인증 불필요)            |     | 인증된 사용자 (JWT 필수)            |
| 토큰: urr-vwr-token (tier:1)       |     | 토큰: urr-entry-token              |
+-----------------------------------+     +-----------------------------------+
```

### 22개 AWS 서비스 사용 목적

| AWS 서비스 | 용도 |
|-----------|------|
| VPC | 5계층 서브넷 네트워크 격리 (Public/App/DB/Cache/Streaming) |
| EKS | 8개 마이크로서비스 + 프론트엔드 컨테이너 오케스트레이션 |
| RDS PostgreSQL | 4개 독립 데이터베이스 (auth/ticket/payment/stats/community) |
| RDS Proxy | DB 연결 풀링, Lambda Cold Start 개선 |
| ElastiCache Redis | 좌석 분산 락, 대기열, Rate Limiting, 캐시 |
| MSK (Kafka) | 서비스 간 비동기 이벤트 스트리밍 (4개 토픽) |
| CloudFront | CDN, Lambda@Edge 토큰 검증, 보안 헤더 |
| Lambda@Edge | CDN 레벨 VWR/Entry 토큰 검증 |
| Lambda (VWR API) | Tier 1 대기열 순번 발급/확인 |
| Lambda (Counter Advancer) | 10초마다 서빙 카운터 전진 |
| Lambda (Ticket Worker) | SQS 이벤트 처리 |
| S3 | 프론트엔드 정적 자산, ALB 로그 저장 |
| ALB | HTTPS 로드밸런싱 (CloudFront 전용 접근) |
| SQS FIFO | 입장 이벤트 큐 + DLQ |
| DynamoDB | VWR 순번 카운터/포지션 테이블 |
| API Gateway | VWR REST API 엔드포인트 |
| IAM | EKS/Lambda/RDS Proxy IRSA 역할 관리 |
| Secrets Manager | RDS/Redis/JWT/Queue 토큰 시크릿 관리 |
| KMS | EKS Secrets 암호화 |
| CloudWatch | 메트릭/로그/알람 (Lambda, MSK, SQS, RDS, EKS) |
| EventBridge | VWR Counter Advancer 1분 스케줄 트리거 |
| VPC Endpoints | 9개 Interface + 2개 Gateway 프라이빗 접근 |

---

## 3. 분석 문서 안내

### 7개 문서 요약

| # | 문서 | 핵심 내용 | 주요 발견사항 |
|---|------|----------|-------------|
| 01 | `01-frontend.md` | Next.js 16 App Router, API 클라이언트, 인증, 대기열 UI, VWR 정적 페이지 | Silent refresh + failedQueue 패턴 우수, SSR 미활용 및 디자인 시스템 부재 |
| 02 | `02-backend.md` | 8개 Spring Boot MSA, DB 구조, Kafka 이벤트, 동시성 처리, API 설계 | 좌석 3중 동시성 보호 탁월, catalog-service DB 공유 및 Map duck-typing 문제 |
| 03 | `03-vwr-queue.md` | 2-Tier VWR 아키텍처, DynamoDB/Redis 대기열, 토큰 플로우, 유량 제어 | CDN 엣지 트래픽 차단 효과적, Lambda@Edge 설정 배포 지연 및 익명 ID 취약 |
| 04 | `04-auth-security.md` | 3-Tier 토큰, JWT Rotation, OWASP 대응, 위협 모델, VPC 보안 | Refresh Token Rotation 우수, admin 하드코딩 비밀번호 CRITICAL 이슈 |
| 05 | `05-infrastructure.md` | Terraform 18모듈, K8s 4환경, CI/CD 15워크플로, ArgoCD, Argo Rollouts | 5계층 서브넷 + Zero-Trust NetworkPolicy 우수, Staging backend 미설정 |
| 06 | `06-monitoring.md` | Prometheus+Grafana, Zipkin, Loki, CloudWatch, 4개 대시보드, 알림 | 8서비스 일관된 옵저버빌리티, Grafana 알림 채널 미설정 및 샘플링 100% |
| 07 | `07-user-flow.md` | 회원가입부터 결제까지 전체 플로우, Kafka 이벤트 흐름, 에러 처리 | Entry Token 검증 이중 보호 우수, Mock 결제 수단 및 transfer-events 미소비 |

### 문서 간 관계

7개 문서는 독립적이지 않다. 핵심 기능이 여러 문서에 걸쳐 분석되어 있다.

```
                    +-- 01-frontend.md (대기열 UI, VWR 정적 페이지)
                    |
03-vwr-queue.md ----+-- 04-auth-security.md (토큰 검증, Lambda@Edge 보안)
(대기열 핵심)       |
                    +-- 05-infrastructure.md (DynamoDB, Lambda, CloudFront Terraform)
                    |
                    +-- 07-user-flow.md (전체 예매 플로우에서의 대기열 위치)

                    +-- 01-frontend.md (API 클라이언트 인터셉터)
                    |
02-backend.md ------+-- 04-auth-security.md (JWT, Gateway 필터 체인)
(서비스 핵심)       |
                    +-- 06-monitoring.md (비즈니스 메트릭, 분산 추적)
                    |
                    +-- 07-user-flow.md (Kafka 이벤트 흐름, Circuit Breaker)
```

---

## 4. 핵심 강점 (Top 10)

7개 분석 문서에서 공통적으로 확인된 가장 두드러진 강점을 종합한다.

| # | 강점 | 설명 | 참조 문서 |
|---|------|------|----------|
| 1 | **좌석 동시성 3중 보호** | Redis Lua 분산 락 + DB 낙관적 잠금(version) + Fencing Token으로 이중 예약을 구조적으로 방지한다. 락 만료 후 결제 진행까지 차단하는 edge case 처리가 특히 우수하다. | `02-backend.md` |
| 2 | **2-Tier VWR 대기열** | CDN 엣지(DynamoDB 원자적 카운터)에서 대량 트래픽을 차단하고, Redis ZSET에서 인증된 사용자를 세밀하게 유량 제어한다. S3 정적 대기 페이지로 거의 무한한 동시접속을 처리한다. | `03-vwr-queue.md`, `01-frontend.md` |
| 3 | **3-Tier 토큰 아키텍처** | Auth JWT(신원) -> VWR Token(CDN 통과) -> Entry Token(API 접근)의 심층 방어로, Lambda@Edge + Gateway + Backend 3단계에서 토큰을 검증한다. userId/eventId 바인딩과 timing-safe 비교를 전면 적용한다. | `04-auth-security.md`, `03-vwr-queue.md` |
| 4 | **이중 경로(Dual-Path) 결제 확정** | payment-service -> ticket-service 동기 REST(primary) + Kafka 이벤트(secondary fallback)으로, 동기 호출 실패 시에도 eventual consistency가 보장된다. | `02-backend.md`, `07-user-flow.md` |
| 5 | **5계층 서브넷 + Zero-Trust NetworkPolicy** | Public/App/DB/Cache/Streaming 분리로 DB/Cache가 인터넷에서 완전 격리되며, K8s default-deny-all 후 서비스별 최소 권한만 허용한다. | `05-infrastructure.md`, `04-auth-security.md` |
| 6 | **Refresh Token Rotation + 재사용 감지** | Family-based 토큰 회전과 재사용 감지(RFC 6749 준수)로, 토큰 탈취 시 자동 감지 및 전체 세션 무효화가 가능하다. | `04-auth-security.md` |
| 7 | **일관된 옵저버빌리티 스택** | 8개 서비스 전체에 동일한 Micrometer + Zipkin + 구조화 로그 패턴을 적용하고, 커스텀 비즈니스 메트릭(예약/결제/대기열)과 4개 Grafana 대시보드를 제공한다. | `06-monitoring.md`, `02-backend.md` |
| 8 | **GitOps CI/CD + Blue/Green 배포** | Reusable Workflow로 8개 서비스가 단일 파이프라인을 공유하며, ArgoCD GitOps + Argo Rollouts Blue/Green(핵심 4서비스)으로 안전한 프로덕션 배포를 지원한다. Trivy 보안 스캔, E2E/부하/카오스 테스트가 내장되어 있다. | `05-infrastructure.md` |
| 9 | **Kafka 이벤트 멱등성 보장** | ticket-service와 stats-service 모두 `processed_events` 테이블 + idempotency_key로 중복 처리를 방지한다. 예약 API에도 클라이언트 생성 멱등성 키가 적용되어 있다. | `02-backend.md`, `07-user-flow.md` |
| 10 | **체계적인 프론트엔드 API 클라이언트** | Silent refresh + failedQueue 패턴, 429 지수 백오프, 서버 시간 NTP 동기화, 카운트다운 무한루프 방지 등 실전 환경에서 필요한 패턴이 완비되어 있다. | `01-frontend.md` |

---

## 5. 핵심 개선 포인트 (Top 10)

7개 분석 문서에서 도출된 가장 중요한 개선 필요 사항을 심각도 순으로 정리한다.

| # | 심각도 | 개선 포인트 | 참조 문서 | AWS 배포 시 해결 방안 |
|---|--------|-----------|----------|---------------------|
| 1 | **P0** | admin 비밀번호 하드코딩 -- Flyway 마이그레이션에 BCrypt 해시가 소스 코드에 노출, Git 이력에 영구 잔존 | `04-auth-security.md` | AWS Secrets Manager에서 초기 비밀번호 주입 + 첫 로그인 시 변경 강제 |
| 2 | **P0** | COOKIE_SECURE 기본값 false -- 프로덕션 HTTPS 환경에서 쿠키 탈취 위험 | `04-auth-security.md` | K8s ConfigMap에서 `COOKIE_SECURE=true` 필수 설정 |
| 3 | **P0** | Staging Terraform backend 미설정 -- 로컬 상태 파일로 팀 협업 불가, 동시 실행 시 상태 손상 | `05-infrastructure.md` | S3 + DynamoDB lock 테이블 추가 |
| 4 | **P1** | catalog-service와 ticket-service DB 공유 -- MSA "Database per Service" 원칙 위반, 스키마 변경 시 양쪽 영향 | `02-backend.md` | catalog_db 분리 + Kafka CQRS 동기화 또는 읽기 전용 프로젝션 |
| 5 | **P1** | SSR 미활용 -- 모든 페이지가 "use client", server-api.ts 미사용, SEO/FCP 저하 | `01-frontend.md` | CloudFront + 서버 컴포넌트 HTML 캐싱으로 성능 개선 |
| 6 | **P1** | VWR Lambda@Edge 설정 배포 지연 -- vwr-active.json이 Lambda 패키지에 번들, 즉각적 VWR 활성화 불가 | `03-vwr-queue.md` | CloudFront Function + KV Store 대안 검토 또는 외부 설정 소스 도입 |
| 7 | **P1** | Mock 결제 수단 -- NaverPay/KakaoPay/계좌이체가 실제 PG 연동 없이 즉시 성공 처리 | `07-user-flow.md` | 프로덕션 배포 전 각 PG사 SDK/API 연동 필수 |
| 8 | **P1** | 백엔드 Egress NetworkPolicy 과도 -- `podSelector: {}`로 네임스페이스 내 모든 Pod 접근 허용 | `04-auth-security.md`, `05-infrastructure.md` | 서비스별 필요 대상만 명시적 허용 + 외부 서비스(RDS/Redis/Kafka) CIDR 규칙 추가 |
| 9 | **P2** | 테스트 인프라 부족 -- Testcontainers 미사용, Kafka 통합 테스트 미흡, payment/stats 비즈니스 메트릭 없음 | `02-backend.md`, `06-monitoring.md` | Testcontainers + spring-kafka-test + payment/stats 커스텀 메트릭 추가 |
| 10 | **P2** | Grafana 알림 채널 미설정 + 프로덕션 Prometheus 배포 미정의 | `06-monitoring.md` | EKS에 kube-prometheus-stack Helm 배포 + SNS/Slack 알림 채널 구성 |

---

## 6. AWS 배포 로드맵

### Phase 1: 필수/즉시 (프로덕션 배포 전 차단 항목)

| 단계 | 작업 | 참고 문서 |
|------|------|----------|
| 1-1 | admin 시드 비밀번호를 Secrets Manager 기반으로 변경, 첫 로그인 시 변경 강제 로직 추가 | `04-auth-security.md` 13장 |
| 1-2 | 프로덕션 환경 `COOKIE_SECURE=true` 설정 확인, VWR API Lambda `CORS_ORIGIN` 필수 설정 | `04-auth-security.md` 14장 |
| 1-3 | Staging Terraform S3 backend + DynamoDB lock 테이블 추가 | `05-infrastructure.md` 10장 |
| 1-4 | ECR URI 플레이스홀더(`CHANGE_ME`) 해결 -- GitHub Secrets 또는 ArgoCD Image Updater 도입 | `05-infrastructure.md` 10장 |
| 1-5 | ALB 보안 그룹에 CloudFront Managed Prefix List만 허용, 직접 접근 차단 | `04-auth-security.md` 14장 |
| 1-6 | NaverPay/KakaoPay/계좌이체 실제 PG SDK 연동 | `07-user-flow.md` 10장 |
| 1-7 | `NEXT_PUBLIC_API_URL` 빌드 시 ARG 주입 검증, `output: "standalone"` 설정으로 Docker 이미지 최적화 | `01-frontend.md` 10장 |

### Phase 2: 중요/단기 (배포 후 1-4주)

| 단계 | 작업 | 참고 문서 |
|------|------|----------|
| 2-1 | catalog-service DB 분리 (catalog_db 생성 + Kafka CQRS 동기화) | `02-backend.md` 9장 |
| 2-2 | 주요 공개 페이지(이벤트 목록/상세, 아티스트)를 서버 컴포넌트로 전환, SSR 활용 | `01-frontend.md` 10장 |
| 2-3 | NetworkPolicy 세분화 -- 백엔드 egress를 서비스별 명시적 허용, 외부 서비스 CIDR 추가 | `04-auth-security.md`, `05-infrastructure.md` |
| 2-4 | AWS WAF를 CloudFront 배포에 연결 (SQL Injection, XSS 필터링) | `04-auth-security.md` 14장 |
| 2-5 | Secrets Manager 자동 회전(Auto-Rotation) 활성화, CloudTrail 감사 로깅 | `04-auth-security.md` 14장 |
| 2-6 | Zipkin 프로덕션 백엔드 결정 (Elasticsearch/Cassandra), 샘플링 비율 0.1로 조정 | `06-monitoring.md` 9장 |
| 2-7 | EKS에 kube-prometheus-stack 배포, Grafana 알림 채널(Slack/SNS) 구성 | `06-monitoring.md` 9장 |
| 2-8 | payment-service, stats-service에 커스텀 비즈니스 메트릭 추가 | `06-monitoring.md` 9장 |

### Phase 3: 권장/장기 (1-6개월)

| 단계 | 작업 | 참고 문서 |
|------|------|----------|
| 3-1 | 공유 라이브러리 모듈(`urr-common`) 추출 -- JwtTokenParser, AuthUser, GlobalExceptionHandler 등 | `02-backend.md` 9장 |
| 3-2 | Kafka Schema Registry(Avro/Protobuf) 도입, duck-typing 제거 | `02-backend.md` 9장 |
| 3-3 | Testcontainers 기반 통합 테스트 환경 구축, E2E 테스트 확장 | `02-backend.md` 9장 |
| 3-4 | 프론트엔드 디자인 시스템 컴포넌트 추출 (Button, Spinner, Badge, Input, Card) | `01-frontend.md` 10장 |
| 3-5 | 모바일 반응형 개선 (햄버거 메뉴, 좌석 맵 pinch-to-zoom) | `01-frontend.md` 10장 |
| 3-6 | Service Mesh(Istio) 도입으로 서비스 간 mTLS 통신, INTERNAL_API_TOKEN 대체 | `04-auth-security.md` 14장 |
| 3-7 | VPC Endpoints 추가 (SQS, MSK용), RDS IAM 인증 전환 | `04-auth-security.md` 14장 |
| 3-8 | 외부 보안 전문 업체 침투 테스트 수행 | `04-auth-security.md` 14장 |

---

## 7. 기술 스택 총정리

### 프론트엔드

| 항목 | 기술 | 버전 |
|------|------|------|
| 프레임워크 | Next.js (App Router) | 16.1.6 |
| UI 라이브러리 | React | 19.2.3 |
| 언어 | TypeScript (strict) | 5.9.3 |
| 스타일링 | Tailwind CSS | v4 |
| 서버 상태 | TanStack React Query | 5.90.21 |
| HTTP 클라이언트 | Axios | 1.13.5 |
| 결제 SDK | TossPayments | 1.9.2 |
| 차트 | Recharts | 3.7.0 |
| 단위 테스트 | Vitest (happy-dom) | 4.0.18 |
| E2E 테스트 | Playwright | 1.58.2 |
| VWR 대기 페이지 | Vanilla JS (번들러 없음, S3 정적 호스팅) | - |

### 백엔드

| 항목 | 기술 | 버전/상세 |
|------|------|----------|
| 프레임워크 | Spring Boot | 3.5.0 |
| 게이트웨이 | Spring Cloud Gateway MVC | 2025.0.1 |
| 언어 | Java (toolchain) | 21 |
| 빌드 도구 | Gradle | 각 서비스 독립 build.gradle |
| ORM/DB | JPA + JdbcTemplate, Flyway 마이그레이션 | - |
| 캐시/락 | Redis (Lettuce) + Lua 스크립트 | 7 |
| 메시징 | Apache Kafka (JsonSerializer) | 3.7.0 |
| 장애 격리 | Resilience4j (CircuitBreaker + Retry) | - |
| 메트릭 | Micrometer + Prometheus | - |
| 분산 추적 | Brave + Zipkin | - |
| 보안 | JWT (jjwt), BCrypt (cost 12) | - |

### 인프라 (AWS)

| 항목 | 기술 | 핵심 설정 |
|------|------|----------|
| 컨테이너 오케스트레이션 | EKS | v1.28, OIDC/IRSA, KMS 암호화 |
| 데이터베이스 | RDS PostgreSQL + RDS Proxy | v16.4, Multi-AZ, gp3 |
| 캐시 | ElastiCache Redis Cluster | 자동 Failover, TLS, AUTH |
| 메시지 브로커 | MSK (Managed Kafka) | v3.6.0, 2 브로커, TLS+IAM |
| CDN | CloudFront + Lambda@Edge | OAC, 보안 헤더, CF Function |
| 대기열 | SQS FIFO + DLQ | 중복 제거, Long Polling |
| VWR 스토리지 | DynamoDB (PAY_PER_REQUEST) | 2 테이블, GSI, TTL |
| 객체 저장소 | S3 | 버전 관리, AES256, 수명 주기 |
| DNS | Route 53 | - |
| IaC | Terraform (18개 모듈) | S3 backend, DynamoDB lock |
| K8s 매니페스트 | Kustomize (4개 환경: dev/kind/staging/prod) | - |

### CI/CD 및 배포

| 항목 | 기술 | 상세 |
|------|------|------|
| CI | GitHub Actions | 15개 워크플로, Reusable Workflow 패턴 |
| CD | ArgoCD | 3개 환경 (dev: 자동, staging: 자동, prod: 수동 sync) |
| 배포 전략 | Argo Rollouts Blue/Green | 핵심 4서비스 (gateway, ticket, payment, queue) |
| 보안 스캔 | Trivy | CRITICAL/HIGH 차단 |
| 테스트 | Playwright E2E + k6 부하/카오스 | staging 게이트 + 주간 스케줄 |
| 컨테이너 | Docker multi-stage (arm64/Graviton) | ECR 레지스트리 |
| 롤백 | 수동 워크플로 (서비스/태그/환경 선택) | Discord 알림 |

### 모니터링

| 항목 | 기술 | 상세 |
|------|------|------|
| 메트릭 수집 | Prometheus | 10초 스크래핑, 7일 보존 |
| 대시보드 | Grafana (4개 대시보드) | Service Overview, JVM, Kafka, Infrastructure |
| 분산 추적 | Zipkin | Brave 브릿지, B3 전파 |
| 로그 수집 | Promtail (DaemonSet) + Loki | 구조화 로그 (traceId/spanId 포함) |
| K8s 알림 | Grafana Alerts | High Error Rate, Service Down, High Latency, Low Disk |
| AWS 알림 | CloudWatch Alarms + SNS | Lambda/SQS DLQ/MSK 장애 감지 |
| AWS 추적 | X-Ray (Lambda), Performance Insights (RDS) | - |

---

## 8. 문서 목록

| 파일명 | 라인 수 | 핵심 주제 |
|--------|---------|----------|
| `01-frontend.md` | 822 | Next.js 16 프론트엔드 아키텍처, API 클라이언트, 인증 흐름, 대기열 UI, VWR 정적 페이지, 상태 관리, 빌드/배포 |
| `02-backend.md` | 1,310 | 8개 Spring Boot MSA 구조, DB 설계, 서비스 간 동기/비동기 통신, 좌석 동시성 3중 보호, 데이터 모델(ERD), API 설계 |
| `03-vwr-queue.md` | 954 | 2-Tier VWR 아키텍처, Tier 1(CDN+DynamoDB+Lambda), Tier 2(Redis ZSET), 토큰 플로우, 유량 제어 전략, 적응형 폴링 |
| `04-auth-security.md` | 995 | 3-Tier 토큰 아키텍처, JWT Rotation, Gateway 필터 체인, VPC/K8s 네트워크 보안, OWASP Top 10 대응, STRIDE 위협 모델 |
| `05-infrastructure.md` | 822 | Terraform 18모듈 구조, VPC 5계층 서브넷, EKS/Kustomize 4환경, CI/CD 15워크플로, ArgoCD GitOps, Argo Rollouts |
| `06-monitoring.md` | 661 | Micrometer+Prometheus 메트릭, Zipkin 분산 추적, Promtail+Loki 로깅, Grafana 4개 대시보드, CloudWatch 알람 |
| `07-user-flow.md` | 1,018 | 회원가입-로그인-이벤트 탐색-대기열-좌석선택-결제 전체 플로우, Kafka 이벤트 흐름, 에러/예외 처리, 관리자 플로우 |
| **합계** | **6,582** | |

---

> 본 보고서는 7개 분석 문서의 핵심 내용을 종합한 요약본이다. 각 항목의 상세 구현, 코드 참조, 파일 경로는 해당 문서를 참조한다.
